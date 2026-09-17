package ru.kavader.arepos.featuregrant

object FeatureGrantKeys {
    val ALL: List<String> = listOf(
        "model.create",
        "model.importPackage",
        "model.export",
        "model.exportDiagramImage",
        "notation.nav",
        "notation.create",
        "notation.import",
        "type.nav",
        "type.create",
        "shape.nav",
        "shape.create",
        "validationScript.nav",
        "validationScript.create",
        "model.tree.createRoot",
        "model.tree.createChildFolder",
        "model.tree.renameDeleteMoveFolder",
        "model.compareVersions",
        "model.relationMatrix",
        "model.wiki.create",
        "model.importOef",
        "model.runValidationScripts",
        "model.inspectJson",
        "model.createBaseline",
    )

    private val KNOWN = ALL.toSet()

    fun isKnown(key: String): Boolean = key in KNOWN
}
