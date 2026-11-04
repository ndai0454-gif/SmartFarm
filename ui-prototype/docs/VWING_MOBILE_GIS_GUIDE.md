# Hướng dẫn tích hợp GIS và bản đồ vệ tinh cho VWING Android/iOS

Đối chiếu tài liệu chính thức ngày 13/10/2026. Đây là hướng dẫn và mã minh họa; chưa tạo project mới, chưa gọi API bằng tài khoản người dùng, chưa build/chạy trên điện thoại.

## 1. Chọn kiến trúc

Đề xuất dùng React Native + TypeScript + Expo Development Build; Mapbox và `@rnmapbox/maps` để hiển thị bản đồ; API riêng + PostgreSQL/PostGIS để quản lý dữ liệu vườn. `@rnmapbox/maps` là thư viện cộng đồng bọc SDK Mapbox native, không phải SDK React Native do Mapbox trực tiếp cung cấp.

Ba phần cần tách:

| Phần | Công dụng | Nguồn |
|---|---|---|
| Bản đồ nền ảnh vệ tinh | Xem địa hình/hiện trạng trực quan phía sau ranh và cây | Mapbox Satellite |
| Dữ liệu GIS của VWING | Ranh vườn/khu, vị trí cây, công việc, lịch sử | API riêng và PostGIS |
| Ảnh viễn thám theo thời gian | Chọn ngày chụp, lọc mây, tính NDVI/thống kê | Copernicus Data Space/Sentinel Hub, thêm sau |

Mapbox là nhà cung cấp bản đồ, không thay thế cơ sở dữ liệu cây. Một bản đồ ảnh màu không cung cấp sẵn băng NIR để tính NDVI. Nền Mapbox là ảnh tổng hợp từ nhiều nguồn, cập nhật không theo lịch cố định; không coi đó là ảnh trực tiếp hoặc ảnh có cùng một ngày chụp trên toàn bản đồ. [Mapbox imagery](https://docs.mapbox.com/data/tilesets/guides/imagery/).

## 2. Luồng dữ liệu

```text
Android/iOS ── SDK + public token ──> Mapbox: bản đồ nền
     │
     └── HTTPS + đăng nhập ──> VWING API ──> PostgreSQL/PostGIS
                                   │
                                   ├──> Kho tệp: ảnh thực địa/drone
                                   └──> Worker viễn thám ──> Copernicus
                                        (OAuth secret ở máy chủ)
```

Ứng dụng tải dữ liệu GIS dạng GeoJSON từ API rồi vẽ chồng lên nền. ID cây bền vững nối điểm trên bản đồ với QR, ảnh và nhật ký. Đổi nhà cung cấp nền không làm mất hồ sơ cây.

## 3. Tạo project mobile mới

Điều kiện: Node.js theo yêu cầu của Expo SDK được chọn; Android Studio/SDK và môi trường Java phù hợp nếu build Android tại máy. Có thể build native bằng EAS trên cloud thay cho cài toàn bộ môi trường build cục bộ. Xem [Expo create-expo-app](https://docs.expo.dev/more/create-expo/), [development builds](https://docs.expo.dev/develop/development-builds/introduction/).

Chạy tại thư mục cha bạn muốn chứa project mới, không chạy đè lên project hiện tại:

```powershell
npx create-expo-app@latest VWingMobile --template blank-typescript
cd VWingMobile
npx expo install @rnmapbox/maps expo-dev-client expo-location
npm install --save-dev @types/geojson
```

Template này dùng `App.tsx`, tránh nhầm với project Expo Router có đường dẫn màn hình khác. Sau khi chọn được bộ phiên bản tương thích, giữ lockfile trong Git. Tài liệu cài RNMapbox hiện ghi hướng dẫn cho dòng 10.2.*; cần đối chiếu hướng dẫn theo phiên bản thực sự được cài, không trộn cấu hình của các dòng SDK. [RNMapbox install](https://rnmapbox.github.io/docs/install).

## 4. Tài khoản và cấu hình Mapbox

1. Tạo tài khoản Mapbox, vào [Access tokens](https://console.mapbox.com/account/access-tokens/).
2. Dùng public access token `pk...` cho phần bản đồ trong app.
3. Tạo `.env` tại gốc project:

```dotenv
EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN=pk.THAY_BANG_PUBLIC_TOKEN_CUA_BAN
```

Public token được đưa vào app đã build; tiền tố `EXPO_PUBLIC_` không giữ nó bí mật. Token bí mật `sk...`, khóa cơ sở dữ liệu và Copernicus client secret không được đặt vào biến public hoặc mã mobile. [Mapbox tokens](https://docs.mapbox.com/help/getting-started/access-tokens/).

Trong `app.json`, thêm plugin vào đối tượng `expo`, giữ các trường có sẵn:

```json
{
  "expo": {
    "name": "VWingMobile",
    "slug": "vwing-mobile",
    "plugins": [
      "@rnmapbox/maps",
      ["expo-location", {
        "locationWhenInUsePermission": "Cho phép VWING lấy vị trí khi bạn ghi nhận cây."
      }]
    ]
  }
}
```

Nếu bản SDK được chọn yêu cầu credential tải native dependency, cấu hình theo trang install của đúng phiên bản trong môi trường build; không chuyển download secret thành token public. Không lấy cấu hình legacy từ bài hướng dẫn cũ để áp dụng tùy ý.

Giữ attribution/logo của nhà cung cấp. Xem giá, quota và chính sách offline tại tài khoản trước khi triển khai; hướng dẫn này không giả định miễn phí vô hạn. Nếu định số hóa/trích xuất dữ liệu thương mại từ ảnh Mapbox, kiểm tra quyền sử dụng dẫn xuất được nêu trong [imagery guide](https://docs.mapbox.com/data/tilesets/guides/imagery/); dữ liệu GPS/đo đạc hoặc ảnh tự sở hữu nên có nguồn riêng.

## 5. Ví dụ App.tsx: nền vệ tinh + ranh minh họa + cây

Thay nội dung `App.tsx` của project mới bằng đoạn sau. Tọa độ và cây dưới đây là dữ liệu tự tạo gần khu vực Bảo Lộc để minh họa, không phải ranh vườn hoặc hiện trạng đã đo. Ví dụ không tự lấy GPS, không lưu backend và chưa có chức năng vẽ ranh.

```tsx
import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Mapbox from '@rnmapbox/maps';
import type { Feature, FeatureCollection, Point, Polygon } from 'geojson';

const token = process.env.EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN;
if (token) Mapbox.setAccessToken(token);

const garden: Feature<Polygon> = {
  type: 'Feature',
  properties: { gardenId: 'demo-garden' },
  geometry: {
    type: 'Polygon',
    coordinates: [[
      [107.8000, 11.5500],
      [107.8010, 11.5500],
      [107.8010, 11.5510],
      [107.8000, 11.5510],
      [107.8000, 11.5500],
    ]],
  },
};

const trees: FeatureCollection<Point> = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature', id: 'demo-a01',
      properties: { treeId: 'demo-a01', code: 'A-01', status: 'unknown' },
      geometry: { type: 'Point', coordinates: [107.8003, 11.5503] },
    },
    {
      type: 'Feature', id: 'demo-a02',
      properties: { treeId: 'demo-a02', code: 'A-02', status: 'watch' },
      geometry: { type: 'Point', coordinates: [107.8006, 11.5503] },
    },
    {
      type: 'Feature', id: 'demo-a03',
      properties: { treeId: 'demo-a03', code: 'A-03', status: 'alert' },
      geometry: { type: 'Point', coordinates: [107.8006, 11.5507] },
    },
  ],
};

export default function App() {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = trees.features.find(f => f.id === selectedId);

  if (!token) {
    return (
      <View style={styles.center}>
        <Text>Thêm public token Mapbox vào .env rồi khởi động lại Metro.</Text>
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <Mapbox.MapView
        style={styles.map}
        styleURL="mapbox://styles/mapbox/standard-satellite"
        onMapLoadingError={() => console.warn('Kiểm tra mạng và token Mapbox')}
      >
        <Mapbox.Camera
          centerCoordinate={[107.8005, 11.5505]}
          zoomLevel={17}
        />

        <Mapbox.ShapeSource id="garden-source" shape={garden}>
          <Mapbox.FillLayer
            id="garden-fill"
            slot="top"
            style={{ fillColor: '#22c55e', fillOpacity: 0.12 }}
          />
          <Mapbox.LineLayer
            id="garden-outline"
            slot="top"
            style={{ lineColor: '#86efac', lineWidth: 3 }}
          />
        </Mapbox.ShapeSource>

        <Mapbox.ShapeSource
          id="tree-source"
          shape={trees}
          onPress={event => {
            const id = event.features[0]?.properties?.treeId;
            if (typeof id === 'string') setSelectedId(id);
          }}
        >
          <Mapbox.CircleLayer
            id="tree-points"
            slot="top"
            style={{
              circleRadius: 8,
              circleStrokeColor: '#ffffff',
              circleStrokeWidth: 2,
              circleColor: [
                'match', ['get', 'status'],
                'alert', '#ef4444',
                'watch', '#f59e0b',
                'stable', '#22c55e',
                '#94a3b8',
              ],
            }}
          />
        </Mapbox.ShapeSource>
      </Mapbox.MapView>

      <View pointerEvents="none" style={styles.banner}>
        <Text style={styles.title}>VWING · Bản đồ minh họa</Text>
        <Text>
          {selected
            ? `Cây ${selected.properties?.code} · ${selected.properties?.status}`
            : 'Chạm một cây để chọn hồ sơ'}
        </Text>
        <Text>Xám: chưa đánh giá · Vàng: theo dõi · Đỏ: cần kiểm tra</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  map: { flex: 1 },
  center: { flex: 1, justifyContent: 'center', padding: 24 },
  banner: {
    position: 'absolute', top: 64, left: 16, right: 16,
    padding: 12, borderRadius: 12, backgroundColor: '#ffffffee',
  },
  title: { fontWeight: '700', marginBottom: 6 },
});
```

Ví dụ dùng Mapbox Standard Satellite với native SDK v11 và các lớp tùy chỉnh trong slot `top`. RNMapbox 10.2 dùng native SDK v11 mặc định theo trang install; không chuyển về native SDK v10 cho ví dụ này. Mapbox hiện khuyên dùng Standard Satellite cho project mới thay cho Satellite Streets classic. [Chuyển sang Standard](https://docs.mapbox.com/map-styles/guides/migrate-to-standard/), [Satellite Streets classic](https://docs.mapbox.com/map-styles/reference/satellite-streets/).

Giữ mặc định attribution/logo. Điểm dùng GeoJSON `[kinh độ, vĩ độ]`, không phải `[vĩ độ, kinh độ]`. `ShapeSource` tiếp nhận dữ liệu và các `FillLayer`, `LineLayer`, `CircleLayer` quyết định cách vẽ. [MapView](https://rnmapbox.github.io/docs/components/MapView), [ShapeSource](https://rnmapbox.github.io/docs/components/ShapeSource), [CircleLayer](https://rnmapbox.github.io/docs/components/CircleLayer).

## 6. Build native và kiểm tra bản đồ

RNMapbox có native code nên không chạy trong Expo Go. [Hướng dẫn cài chính thức của thư viện](https://rnmapbox.github.io/docs/install).

Android cục bộ, sau khi có emulator hoặc máy kết nối USB và môi trường Android đã sẵn sàng:

```powershell
npx expo run:android
```

Các lần phát triển JS tiếp theo dùng:

```powershell
npx expo start --dev-client
```

Đổi plugin/native dependency cần build native lại. Thay `.env` cần khởi động lại Metro; EAS Build cũng phải được cấp biến môi trường tương ứng, không giả định file `.env` bị Git bỏ qua sẽ tự xuất hiện trên cloud.

iOS trên máy Windows: không build iOS cục bộ bằng Xcode. Dùng Mac/Xcode hoặc EAS cloud. Sau khi cấu hình Expo project và credential Apple theo hướng dẫn, có thể dùng:

```powershell
npx eas-cli@latest login
npx eas-cli@latest build:configure
npx eas-cli@latest build --profile development --platform ios
```

Trong `eas.json`, profile `development` cần `developmentClient: true` và cấu hình phân phối phù hợp. Cài lên iPhone thật qua EAS cần tài khoản/credential Apple phù hợp; bản simulator chạy trên Mac. Xem [EAS Build](https://docs.expo.dev/build/introduction/) và [development builds](https://docs.expo.dev/develop/development-builds/introduction/).

Kết quả bước này: thấy nền vệ tinh, ranh và 3 điểm minh họa; chạm điểm đổi thông tin. Chưa có cây/ranh thật được lưu.

## 7. Thêm vị trí GPS thật

Chỉ xin quyền vị trí khi người dùng chọn chức năng định vị/thêm cây bằng GPS. Việc xem bản đồ và nhập tọa độ tay không cần bắt buộc quyền vị trí.

Ví dụ helper trong app, gọi từ một nút do người dùng bấm:

```ts
import * as Location from 'expo-location';

export async function captureTreeLocation() {
  const permission = await Location.requestForegroundPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new Error('Chưa có quyền vị trí; bạn có thể chọn điểm trên bản đồ.');
  }
  const result = await Location.getCurrentPositionAsync({
    accuracy: Location.Accuracy.High,
  });
  return {
    geometry: {
      type: 'Point' as const,
      coordinates: [result.coords.longitude, result.coords.latitude],
    },
    accuracyMeters: result.coords.accuracy,
    capturedAt: new Date(result.timestamp).toISOString(),
    locationSource: 'device-gps',
  };
}
```

Lưu cả nguồn và accuracy ước lượng. GPS điện thoại có thể không phân biệt chắc chắn hai cây gần nhau; cho sửa điểm và xác nhận bằng QR/ID. Không dùng tọa độ làm ID cây. Xem [Expo Location](https://docs.expo.dev/versions/latest/sdk/location/).

## 8. Backend GIS bằng PostGIS

Bạn có thể dùng Spring Boot hoặc backend quen thuộc; giao thức mobile vẫn là HTTPS/JSON. Mobile không kết nối thẳng PostgreSQL. Tạo database đã cài PostGIS, rồi dùng migration cho ví dụ tối thiểu:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE gardens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  boundary geometry(Polygon, 4326),
  CHECK (boundary IS NULL OR (
    ST_IsValid(boundary) AND NOT ST_IsEmpty(boundary)
  ))
);

CREATE TABLE trees (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  garden_id uuid NOT NULL REFERENCES gardens(id),
  code text NOT NULL,
  location geometry(Point, 4326),
  location_source text,
  accuracy_meters double precision CHECK (accuracy_meters >= 0),
  observed_at timestamptz,
  status text NOT NULL DEFAULT 'unknown',
  UNIQUE (garden_id, code)
);

CREATE INDEX gardens_boundary_gist ON gardens USING gist(boundary);
CREATE INDEX trees_location_gist ON trees USING gist(location);
CREATE INDEX trees_location_geog_gist ON trees USING gist((location::geography));
```

Đây chỉ là lõi không gian; thêm quyền thành viên, nhật ký và quản lý phiên bản theo nghiệp vụ VWING. Cây có thể chưa có vị trí; vườn có nhiều phần rời nhau thì dùng MultiPolygon thay vì Polygon và xử lý tương ứng. API kiểm tra longitude/latitude hợp lệ, ID thuộc vườn được phép và hình học đầu vào.

Ví dụ truy vấn, `$1`, `$2`, `$3` là tham số bind của backend:

```sql
-- Diện tích hecta, không tính trên độ vuông của EPSG:4326.
SELECT ST_Area(boundary::geography) / 10000.0 AS hectares
FROM gardens WHERE id = $1;

-- Cây trong phạm vi 20 m của một điểm, có lọc theo vườn.
SELECT id, code
FROM trees
WHERE garden_id = $1
  AND ST_DWithin(
    location::geography,
    ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography,
    20
  );

-- Dữ liệu hình học để backend ghép thành GeoJSON Feature.
SELECT id, code, status, ST_AsGeoJSON(location)::json AS geometry
FROM trees WHERE garden_id = $1 AND location IS NOT NULL;
```

`geography` cho phép tính diện tích/khoảng cách bằng đơn vị mét phù hợp các truy vấn này; geometry 4326 giữ tọa độ lon/lat để trao đổi. [ST_Area](https://postgis.net/docs/ST_Area.html), [ST_DWithin](https://postgis.net/docs/ST_DWithin.html).

API kiểm tra cây nằm trong hoặc trên ranh bằng `ST_Covers(boundary, location)`, nhưng cho phép quy trình xác minh nếu điểm GPS lệch nhẹ thay vì tự chuyển cây sang vườn khác. [ST_Covers](https://postgis.net/docs/ST_Covers.html).

## 9. API VWING và kết nối vào màn hình map

Đề xuất hợp đồng API, chưa có server triển khai:

| Endpoint | Chức năng |
|---|---|
| `GET /api/gardens/{id}/map` | GeoJSON FeatureCollection ranh/khu và cây người dùng được xem |
| `PATCH /api/gardens/{id}/boundary` | Lưu ranh, nguồn và phiên bản |
| `POST /api/gardens/{id}/trees` | Thêm cây và tọa độ nếu có |
| `PATCH /api/trees/{id}/location` | Sửa tọa độ, giữ nguyên ID và lịch sử |
| `GET /api/trees/{id}/events` | Mở hồ sơ khi bấm điểm/QR |

Trong FeatureCollection, mỗi feature có `properties.kind = garden | zone | tree`, `properties.treeId` cho cây và `id` bền vững. Có thể tách API/lớp ranh và cây để quản lý đơn giản. Đối với bản đồ lớn, thêm bounding box, giới hạn số đối tượng hoặc vector tiles; không tải toàn bộ mọi vườn mỗi lần kéo map.

Ví dụ gọi trong mobile:

```ts
const response = await fetch(`${apiBase}/api/gardens/${gardenId}/map`, {
  headers: { Authorization: `Bearer ${sessionToken}` },
});
if (!response.ok) throw new Error(`Tải bản đồ thất bại: ${response.status}`);
const geojson = await response.json();
// Kiểm tra cấu trúc response; phân loại feature theo kind,
// rồi truyền dữ liệu vào ShapeSource qua state của React.
```

Đó là token đăng nhập VWING, không phải public Mapbox token. Server phải xác thực quyền vườn ở mọi endpoint, không chỉ tin `gardenId` gửi từ điện thoại. Dùng HTTPS và cấu hình URL API theo môi trường; trên điện thoại `localhost` là chính điện thoại, không phải máy Windows đang chạy backend.

Vẽ ranh: thêm chế độ “Vẽ”, chạm map lấy các điểm lon/lat, có hoàn tác; cần ít nhất 3 đỉnh khác nhau và đóng vòng bằng cách thêm lại đỉnh đầu. Map SDK hỗ trợ vẽ lớp nhưng bạn vẫn phải viết thao tác chỉnh sửa. Backend kiểm tra ranh không tự cắt trước khi lưu; diện tích do server tính. Với đo đạc/nhập ảnh, giữ metadata nguồn, không giả định vẽ tay trên nền ảnh có độ chính xác khảo sát.

## 10. Khi cần Sentinel-2/NDVI

Chỉ thêm phần này sau khi bản đồ và dữ liệu vườn hoạt động. Dùng [Copernicus Data Space](https://dataspace.copernicus.eu/), tạo OAuth client Sentinel Hub. Giữ client secret tại backend. [Authentication](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Overview/Authentication.html).

Luồng:

1. App gửi ID vườn, khoảng ngày và loại sản phẩm mong muốn đến VWING API.
2. Backend lấy ranh vườn, kiểm tra quyền và giới hạn yêu cầu.
3. Backend lấy/reuse OAuth token theo hạn dùng, không xin token mới cho mỗi tile.
4. Catalog API tìm cảnh trong vùng và thời gian; lọc chất lượng/mây phù hợp.
5. Process API tạo ảnh; Statistical API hoặc xử lý raster tính thống kê theo ranh khi cần.
6. Lưu kết quả cùng nguồn cảnh, ngày, độ phân giải, cloud mask, cách ghép cảnh và phiên bản xử lý.
7. Trả layer đã chuẩn bị cho mobile để xem chồng lên bản đồ.

Token endpoint:

```text
https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token
```

Process endpoint theo tài liệu CDSE:

```text
https://sh.dataspace.copernicus.eu/process/v1
```

Dùng Request Builder của CDSE để thử true-color vùng vườn trước rồi mới NDVI; tránh tự ghép endpoint giữa hệ CDSE và tài khoản Sentinel Hub khác. Xem [Beginners Guide](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/UserGuides/BeginnersGuide.html).

Sentinel-2 NDVI thường dùng B08 (NIR) và B04 (Red); đọc collection/đơn vị phản xạ, loại mây/bóng mây/no-data và trường hợp mẫu số không hợp lệ. Không dùng RGB của basemap để tính NDVI. Không suy thiếu nước/bệnh cụ thể chỉ từ NDVI. Độ phân giải không được tăng thực sự chỉ vì yêu cầu server xuất ảnh nhiều pixel hoặc zoom map sâu hơn. [Sentinel-2 L2A: băng và xử lý](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Data/S2L2A.html).

Hiển thị: một ảnh PNG có bounds nhỏ có thể dùng ImageSource + RasterLayer và bốn góc địa lý chính xác; ảnh lớn/chuỗi thời gian nên phục vụ raster tiles qua RasterSource. TIFF định lượng phục vụ tính toán khác PNG tô màu để xem. Một PNG tô màu không thay thế raster NDVI số để tính trung bình. Worker/cache theo vườn/ngày/phương pháp giúp tránh xử lý lại mỗi lần người dùng kéo bản đồ.

Offline có hai phần riêng: lưu hồ sơ/GeoJSON bằng SQLite và tải bản đồ bằng cơ chế offline SDK theo quyền/quota nhà cung cấp. Lưu hồ sơ thành công chưa đảm bảo ảnh nền sẵn sàng khi mất mạng. Xem liên kết offline trong [Mapbox imagery](https://docs.mapbox.com/data/tilesets/guides/imagery/).

## 11. Thứ tự hoàn thành và kiểm tra

1. Nền vệ tinh + một ranh và ba cây mẫu hiển thị đúng ở Android development build.
2. Chạm cây mở đúng ID; tọa độ lon/lat không bị đảo.
3. Nhập cây/ranh thật và lưu PostGIS; mở lại lấy đúng dữ liệu từ API.
4. Thêm GPS, chỉnh vị trí, QR và liên kết lịch sử cây; thử khi từ chối quyền vị trí.
5. Lưu offline/đồng bộ, không nhân đôi cây hoặc mất chỉnh sửa.
6. Build iOS và kiểm tra cùng luồng, attribution và thao tác chạm.
7. Thêm một sản phẩm Sentinel-2 theo khoảng ngày; không hiển thị cảnh bị che mây như kết quả hợp lệ, ghi rõ nếu ghép nhiều ngày.

Các lỗi thường gặp: map trắng do token/quyền/mạng; native module thiếu do mở Expo Go hoặc chưa build lại; cây nằm sai chỗ do đảo tọa độ; diện tích sai vì tính trên độ; API không đến máy tính do dùng localhost; dữ liệu mất vì chỉ dùng state; ảnh nhiều ngày bị mô tả sai như một lần chụp.

Chưa có bằng chứng runtime trong hướng dẫn này. Bước đầu tiên để triển khai thật là tạo project tại đường dẫn bạn chọn, cấu hình public token Mapbox, rồi build thử map trên thiết bị.
