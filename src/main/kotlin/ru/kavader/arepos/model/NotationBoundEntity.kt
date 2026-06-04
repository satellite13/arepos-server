package ru.kavader.arepos.model

interface NotationBoundEntity {
    var name: String
    var attrs: String?
    var version: String
    var owner: Users
    var notation: Notations
}
