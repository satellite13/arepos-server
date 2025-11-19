package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface LinksRepository : JpaRepository<Links, UUID> {
    fun findByModel(model: Models, pageable: Pageable): Page<Links>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Links>
    fun findBySource(source: Nodes, pageable: Pageable): Page<Links>
    fun findByTarget(target: Nodes, pageable: Pageable): Page<Links>
    fun findByLinkType(linkType: LinkTypes, pageable: Pageable): Page<Links>
    fun findByModelAndOwner(model: Models, owner: Users, pageable: Pageable): Page<Links>
}


