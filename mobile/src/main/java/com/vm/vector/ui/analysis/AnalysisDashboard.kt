package com.vm.vector.ui.analysis

enum class AnalysisDashboard(val title: String) {
    Overview("Overview"),
    Routine("Routine"),
    Food("Food"),
    Training("Training"),
    Body("Body"),
    Sleep("Sleep"),
    Self("Self"),
    ;

    companion object {
        val pickerEntries: List<AnalysisDashboard> = entries
    }
}
