package ru.kavader.arepos.model

interface CatalogTypeEntity {
    var name: String
    var attrs: String?
    var owner: Users
}
