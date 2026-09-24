package com.tomeofhealing.app.ui.components

import com.tomeofhealing.app.model.NoteTemplate
import com.tomeofhealing.app.model.SortMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTimestamp(value: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(value))

fun SortMode.label(): String = when (this) {
    SortMode.MANUAL -> "Manual"
    SortMode.ALPHABETICAL -> "Alphabetical"
    SortMode.NEWEST_MODIFIED -> "Newest modified"
    SortMode.OLDEST_MODIFIED -> "Oldest modified"
    SortMode.NEWEST_CREATED -> "Newest created"
    SortMode.OLDEST_CREATED -> "Oldest created"
}

fun NoteTemplate.label(): String = when (this) {
    NoteTemplate.DISEASE -> "Disease"
    NoteTemplate.DRUG -> "Drug"
    NoteTemplate.PROCEDURE -> "Procedure"
    NoteTemplate.EMERGENCY -> "Emergency"
    NoteTemplate.ECG -> "ECG"
    NoteTemplate.RADIOLOGY -> "Radiology"
    NoteTemplate.ANATOMY -> "Anatomy"
    NoteTemplate.DIFFERENTIAL_DIAGNOSIS -> "Differential diagnosis"
    NoteTemplate.BLANK -> "Blank"
}
