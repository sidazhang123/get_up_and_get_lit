package com.getupandgetlit.dingshihai.domain.model

sealed class TaskValidationResult {
    data object Valid : TaskValidationResult()
    data object MissingRequired : TaskValidationResult()
    data object InvalidTime : TaskValidationResult()
    data object DuplicateTime : TaskValidationResult()
    data object InvalidFileType : TaskValidationResult()
    data object InvalidInterval : TaskValidationResult()
}

