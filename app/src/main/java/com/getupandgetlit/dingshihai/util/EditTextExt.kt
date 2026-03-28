package com.getupandgetlit.dingshihai.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun EditText.doAfterTextChangedCompat(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            action.invoke(s?.toString().orEmpty())
        }
    })
}

