package com.example.zodiactableapp.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import java.util.Locale

/** 给日期选择器用的简体中文 Context（月份标题、星期表头、按钮都跟随它）。 */
fun localizedDatePickerContext(context: Context): Context {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale.SIMPLIFIED_CHINESE)
    return ContextThemeWrapper(context, context.applicationInfo.theme).apply {
        applyOverrideConfiguration(config)
    }
}

/** 日期选择器：按钮固定显示中文。 */
fun DatePickerDialog.showLocalized() {
    setOnShowListener {
        getButton(DialogInterface.BUTTON_POSITIVE)?.text = "确定"
        getButton(DialogInterface.BUTTON_NEGATIVE)?.text = "取消"
    }
    show()
}
