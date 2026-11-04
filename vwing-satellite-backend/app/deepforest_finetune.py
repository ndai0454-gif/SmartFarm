"""Real DeepForest fine-tuning from user-verified boxes on an uploaded RGB image.

Training runs on CPU in a background thread. The image is split by a vertical
line so that validation boxes come from ground the model never trained on; the
reported metrics are measured by DeepForest's own validation loop before and
after training. A checkpoint is written only when training finishes.
"""

from __future__ import annotations

import json
import math
import shutil
import threading
import time
import uuid
import warnings

import numpy as np
import pandas as pd

from . import deepforest_runtime as dr

MIN_TRAIN_BOXES = 6
MIN_VAL_BOXES = 2
MIN_BOX_PX = 4
VAL_FRACTION = 0.2
MAX_JOBS = 50

_jobs: dict[str, dict] = {}
_jobs_lock = threading.Lock()
_train_lock = threading.Lock()  # one CPU training run at a time


class FineTuneInputError(ValueError):
    pass


def _geo_to_pixel_boxes(annotations, bounds, width, height) -> list[tuple]:
    dx, dy = bounds["east"] - bounds["west"], bounds["north"] - bounds["south"]
    boxes = []
    for a in annotations:
        x0 = (a.west - bounds["west"]) / dx * width
        x1 = (a.east - bounds["west"]) / dx * width
        y0 = (bounds["north"] - a.north) / dy * height
        y1 = (bounds["north"] - a.south) / dy * height
        x0, x1 = max(0.0, min(x0, x1)), min(float(width), max(x0, x1))
        y0, y1 = max(0.0, min(y0, y1)), min(float(height), max(y0, y1))
        if x1 - x0 >= MIN_BOX_PX and y1 - y0 >= MIN_BOX_PX:
            boxes.append((x0, y0, x1, y1))
    return boxes


def split_train_validation(boxes, width) -> tuple[int, list, list]:
    """Hold out the right-most ~20% of boxes; boxes crossing the cut are dropped."""
    if len(boxes) < MIN_TRAIN_BOXES + MIN_VAL_BOXES:
        raise FineTuneInputError(
            f"Cần ít nhất {MIN_TRAIN_BOXES + MIN_VAL_BOXES} khung nhãn hợp lệ "
            f"(≥{MIN_BOX_PX}px); hiện có {len(boxes)}."
        )
    ordered = sorted(boxes, key=lambda b: b[0])
    n_val = max(MIN_VAL_BOXES, math.ceil(len(ordered) * VAL_FRACTION))
    cut = int(math.floor(ordered[-n_val][0]))
    train = [b for b in ordered if b[2] <= cut]
    val = [(b[0] - cut, b[1], b[2] - cut, b[3]) for b in ordered if b[0] >= cut]
    if len(train) < MIN_TRAIN_BOXES or len(val) < MIN_VAL_BOXES or cut < 64:
        raise FineTuneInputError(
            "Không tách được tập huấn luyện/kiểm định độc lập: các khung nhãn chồng "
            "lấn hoặc dồn một phía ảnh. Hãy gán nhãn trải đều trên ảnh."
        )
    if width - cut < 64:
        raise FineTuneInputError("Vùng kiểm định quá hẹp; gán nhãn trải rộng hơn.")
    return cut, train, val


def _write_split(rgb, boxes, label, name, patch_size, overlap, out_dir) -> str:
    """Cut `rgb` into patches with boxes and write {out_dir}/{name}.csv."""
    from deepforest import preprocess

    out_dir.mkdir(parents=True, exist_ok=True)
    # The model is scale-sensitive (tiles are resized up to its input size), so a
    # crop smaller than the patch is padded with black rather than shrinking patches.
    pad_h, pad_w = max(0, patch_size - rgb.shape[0]), max(0, patch_size - rgb.shape[1])
    if pad_h or pad_w:
        rgb = np.pad(rgb, ((0, pad_h), (0, pad_w), (0, 0)))
    patch = patch_size
    df = pd.DataFrame(
        [
            {
                "image_path": f"{name}.png",
                "xmin": b[0],
                "ymin": b[1],
                "xmax": b[2],
                "ymax": b[3],
                "label": label,
            }
            for b in boxes
        ]
    )
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        patches = preprocess.split_raster(
            annotations_file=df,
            numpy_image=np.ascontiguousarray(rgb.transpose(2, 0, 1)),
            image_name=f"{name}.png",
            patch_size=patch,
            patch_overlap=overlap,
            root_dir=str(out_dir),
            allow_empty=False,
            save_dir=str(out_dir),
        )
    if patches is None or len(patches) == 0:
        raise FineTuneInputError("Không tạo được mảnh ảnh huấn luyện có nhãn.")
    csv_path = out_dir / f"{name}.csv"
    patches.to_csv(csv_path, index=False)
    return str(csv_path), int(patches["image_path"].nunique())


def _metric(values: dict, *keys: str) -> float | None:
    for key in keys:
        if key in values and values[key] is not None:
            value = float(values[key])
            if math.isfinite(value):
                return value
    return None


def _validate(model, csv_path, root_dir) -> dict:
    model.config.validation.csv_file = csv_path
    model.config.validation.root_dir = root_dir
    model.config.validation.val_accuracy_interval = 1
    model.create_trainer(logger=False)
    model.trainer.validate(model, verbose=False)
    return {
        k: float(v)
        for k, v in model.trainer.callback_metrics.items()
        if hasattr(v, "item") or isinstance(v, (int, float))
    }


def _recorder():
    import pytorch_lightning as pl

    class Recorder(pl.Callback):
        def __init__(self):
            self.rows = []

        def on_validation_epoch_end(self, trainer, module):
            if trainer.sanity_checking:
                return
            row = {
                k: float(v)
                for k, v in trainer.callback_metrics.items()
                if hasattr(v, "item") or isinstance(v, (int, float))
            }
            row["epoch"] = trainer.current_epoch + 1
            self.rows.append(row)

    return Recorder()


def train(job: dict, req, rgb: np.ndarray, bounds: dict) -> dict:
    """Blocking training run. Raises FineTuneInputError/RuntimeUnavailable."""
    height, width = rgb.shape[:2]
    boxes = _geo_to_pixel_boxes(req.annotations, bounds, width, height)
    cut, train_boxes, val_boxes = split_train_validation(boxes, width)

    checkpoint_id = f"ft-{uuid.uuid4().hex[:12]}"
    final_dir = dr.CHECKPOINT_DIR / checkpoint_id
    work = final_dir.with_name(checkpoint_id + ".partial")
    try:
        job["stage"] = "loading_base_model"
        try:
            model = dr.DeepForestRuntime.build_model(req.base_model_id)
        except Exception as exc:
            raise dr.RuntimeUnavailable(
                "Không nạp được mô hình nền để fine-tune."
            ) from exc
        label = next(iter(model.label_dict))

        job["stage"] = "preparing_patches"
        train_csv, train_patches = _write_split(
            rgb[:, :cut], train_boxes, label, "train", req.patch_size,
            req.patch_overlap, work / "train",
        )
        val_csv, val_patches = _write_split(
            rgb[:, cut:], val_boxes, label, "val", req.patch_size,
            req.patch_overlap, work / "val",
        )

        model.config.train.csv_file = train_csv
        model.config.train.root_dir = str(work / "train")
        model.config.train.epochs = req.epochs
        model.config.train.lr = req.learning_rate
        model.config.log_root = str(work / "logs")

        job["stage"] = "baseline_validation"
        initial = _validate(model, val_csv, str(work / "val"))

        job["stage"] = "training"
        recorder = _recorder()
        model.create_trainer(callbacks=[recorder])  # LR monitor needs the CSV logger
        model.trainer.fit(model)
        job["stage"] = "final_validation"
        model.model.eval()
        final = _validate(model, val_csv, str(work / "val"))

        job["stage"] = "saving"
        model.trainer.save_checkpoint(str(work / "model.ckpt"))
        shutil.rmtree(work / "logs", ignore_errors=True)
        shutil.rmtree(work / "train", ignore_errors=True)
        shutil.rmtree(work / "val", ignore_errors=True)

        result = {
            "checkpoint_id": checkpoint_id,
            "base_model_id": req.base_model_id,
            "crop_type": req.crop_type,
            "label": label,
            "training_annotations_count": len(train_boxes),
            "validation_annotations_count": len(val_boxes),
            "dropped_annotations_count": len(req.annotations)
            - len(train_boxes)
            - len(val_boxes),
            "augmented_patches_count": train_patches,
            "validation_patches_count": val_patches,
            "epochs_completed": req.epochs,
            "learning_rate": req.learning_rate,
            "initial_map50": _metric(initial, "map_50"),
            "final_map50": _metric(final, "map_50"),
            "initial_recall": _metric(initial, "box_recall"),
            "final_recall": _metric(final, "box_recall"),
            "epoch_history": [
                {
                    "epoch": int(row["epoch"]),
                    "box_regression_loss": _metric(row, "train_bbox_regression_epoch", "train_bbox_regression"),
                    "focal_classification_loss": _metric(row, "train_classification_epoch", "train_classification"),
                    "total_loss": _metric(row, "train_loss_epoch", "train_loss"),
                    "val_map50": _metric(row, "map_50"),
                    "val_recall": _metric(row, "box_recall"),
                }
                for row in recorder.rows
            ],
            "split": {
                "method": "vertical hold-out: right-most ~20% of boxes validate",
                "cut_x_px": cut,
            },
            "created_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }
        (work / "meta.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        work.rename(final_dir)
        return result
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise


# ---- background jobs -------------------------------------------------------

def _prune():
    finished = [k for k, v in _jobs.items() if v["state"] in ("done", "failed")]
    for key in finished[: max(0, len(_jobs) - MAX_JOBS)]:
        _jobs.pop(key, None)


def start_job(req, rgb, bounds) -> dict:
    job = {
        "job_id": uuid.uuid4().hex,
        "state": "queued",
        "stage": "queued",
        "result": None,
        "error": None,
        "created_at": time.time(),
    }
    with _jobs_lock:
        _prune()
        _jobs[job["job_id"]] = job

    def run():
        with _train_lock:
            job["state"] = "running"
            try:
                job["result"] = train(job, req, rgb, bounds)
                job["state"] = "done"
            except (FineTuneInputError, dr.RuntimeUnavailable) as exc:
                job["state"], job["error"] = "failed", str(exc)
            except Exception as exc:  # training crash: report class only
                job["state"] = "failed"
                job["error"] = f"Huấn luyện thất bại ({type(exc).__name__}); xem log máy chủ."
                import logging

                logging.getLogger("uvicorn.error").exception("deepforest finetune")
            job["stage"] = job["state"]

    threading.Thread(target=run, name=f"df-finetune-{job['job_id'][:8]}", daemon=True).start()
    return job


def get_job(job_id: str) -> dict | None:
    return _jobs.get(job_id)


def list_checkpoints() -> list[dict]:
    out = []
    if dr.CHECKPOINT_DIR.is_dir():
        for meta in sorted(dr.CHECKPOINT_DIR.glob("ft-*/meta.json")):
            try:
                out.append(json.loads(meta.read_text(encoding="utf-8")))
            except (OSError, ValueError):
                continue
    return out
