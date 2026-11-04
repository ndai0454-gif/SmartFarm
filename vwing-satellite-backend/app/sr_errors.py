"""Explicitly curated, safe errors that SR jobs may return to users."""


class SrInputError(ValueError):
    """An actionable input error whose message is safe for the public API.

    Callers must supply a curated message, never an upstream exception or secret.
    Other exception types are deliberately hidden by the job manager.
    """

    def __init__(self, message: str):
        super().__init__(message)
        self.user_message = message
