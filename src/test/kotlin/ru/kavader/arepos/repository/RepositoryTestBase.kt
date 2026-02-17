package ru.kavader.arepos.repository

import org.springframework.beans.factory.annotation.Autowired
import ru.kavader.arepos.model.*
import ru.kavader.arepos.support.PostgresContainerTest
import java.time.Instant
import java.util.UUID

abstract class RepositoryTestBase : PostgresContainerTest() {

    @Autowired
    protected lateinit var usersRepository: UsersRepository
    @Autowired
    protected lateinit var auditLogRepository: AuditLogRepository
    @Autowired
    protected lateinit var modelsRepository: ModelsRepository
    @Autowired
    protected lateinit var notationsRepository: NotationsRepository
    @Autowired
    protected lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired
    protected lateinit var nodesRepository: NodesRepository
    @Autowired
    protected lateinit var componentsRepository: ComponentsRepository
    @Autowired
    protected lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired
    protected lateinit var linksRepository: LinksRepository
    @Autowired
    protected lateinit var relationsRepository: RelationsRepository
    @Autowired
    protected lateinit var relationRulesRepository: RelationRulesRepository
    @Autowired
    protected lateinit var diagramsRepository: DiagramsRepository

    protected fun persistUser(
        email: String = "user-${randomSuffix()}@example.com",
        attrs: String? = """{"role":"tester"}"""
    ): Users = usersRepository.save(
        Users(
            email = email,
            attrs = attrs,
            createdAt = Instant.now()
        )
    )

    protected fun persistNodeType(
        owner: Users = persistUser(),
        name: String = "node-type-${randomSuffix()}",
        attrs: String? = """{"color":"#ff0000"}"""
    ): NodeTypes = nodeTypesRepository.save(
        NodeTypes(
            name = name,
            attrs = attrs,
            createdAt = Instant.now(),
            owner = owner
        )
    )

    protected fun persistModel(
        owner: Users = persistUser(),
        name: String = "model-${randomSuffix()}",
        version: String = "1.0.0",
        attrs: String? = """{"kind":"default"}"""
    ): Models = modelsRepository.save(
        Models(
            name = name,
            createdAt = Instant.now(),
            attrs = attrs,
            version = version,
            owner = owner
        )
    )

    protected fun persistNotation(
        owner: Users = persistUser(),
        name: String = "notation-${randomSuffix()}",
        version: String = "1.0.0",
        attrs: String? = """{"format":"json"}"""
    ): Notations = notationsRepository.save(
        Notations(
            owner = owner,
            attrs = attrs,
            createdAt = Instant.now(),
            name = name,
            version = version
        )
    )

    protected fun persistLinkType(
        owner: Users = persistUser(),
        name: String = "link-type-${randomSuffix()}",
        attrs: String? = """{"directional":true}"""
    ): LinkTypes = linkTypesRepository.save(
        LinkTypes(
            createdAt = Instant.now(),
            name = name,
            attrs = attrs,
            owner = owner
        )
    )

    protected fun persistComponent(
        notation: Notations = persistNotation(),
        nodeType: NodeTypes = persistNodeType(owner = notation.owner),
        owner: Users = notation.owner,
        name: String = "component-${randomSuffix()}",
        version: String = "1.0.0",
        attrs: String? = """{"size":1}"""
    ): Components = componentsRepository.save(
        Components(
            name = name,
            attrs = attrs,
            createdAt = Instant.now(),
            version = version,
            notation = notation,
            owner = owner,
            nodeType = nodeType
        )
    )

    protected fun persistRelation(
        notation: Notations = persistNotation(),
        linkType: LinkTypes = persistLinkType(owner = notation.owner),
        owner: Users = notation.owner,
        name: String = "relation-${randomSuffix()}",
        version: String = "1.0.0",
        attrs: String? = """{"weight":1}"""
    ): Relations = relationsRepository.save(
        Relations(
            attrs = attrs,
            createdAt = Instant.now(),
            version = version,
            owner = owner,
            notation = notation,
            name = name,
            linkType = linkType
        )
    )

    protected fun persistRelationRule(
        relation: Relations = persistRelation(),
        fromComponent: Components = persistComponent(notation = relation.notation, owner = relation.owner),
        toComponent: Components = persistComponent(notation = relation.notation, owner = relation.owner)
    ): RelationRules = relationRulesRepository.save(
        RelationRules(
            createdAt = Instant.now(),
            owner = relation.owner,
            attrs = """{"bidirectional":false}""",
            relation = relation,
            fromComponent = fromComponent,
            toComponent = toComponent
        )
    )

    protected fun persistNode(
        model: Models = persistModel(),
        owner: Users = model.owner,
        nodeType: NodeTypes = persistNodeType(owner = owner),
        name: String = "node-${randomSuffix()}",
        attrs: String? = """{"label":"node"}""",
        parent: Nodes? = null
    ): Nodes = nodesRepository.save(
        Nodes(
            name = name,
            createdAt = Instant.now(),
            attrs = attrs,
            parentNode = parent,
            model = model,
            owner = owner,
            nodeType = nodeType
        )
    )

    protected fun persistLink(
        model: Models = persistModel(),
        owner: Users = model.owner,
        nodeType: NodeTypes = persistNodeType(owner = owner),
        linkType: LinkTypes = persistLinkType(owner = owner),
        source: Nodes = persistNode(model = model, owner = owner, nodeType = nodeType),
        target: Nodes = persistNode(model = model, owner = owner, nodeType = nodeType),
        attrs: String? = """{"strength":0.5}"""
    ): Links = linksRepository.save(
        Links(
            source = source,
            target = target,
            attrs = attrs,
            createdAt = Instant.now(),
            owner = owner,
            linkType = linkType,
            model = model
        )
    )

    protected fun persistDiagram(
        model: Models = persistModel(),
        notation: Notations = persistNotation(owner = model.owner),
        owner: Users = model.owner,
        node: Nodes? = null,
        name: String = "diagram-${randomSuffix()}",
        version: String = "1.0.0",
        attrs: String? = """{"layout":"default"}"""
    ): Diagrams = diagramsRepository.save(
        Diagrams(
            name = name,
            createdAt = Instant.now(),
            attrs = attrs,
            version = version,
            owner = owner,
            model = model,
            notation = notation,
            node = node
        )
    )

    protected fun persistAuditLog(
        tableName: String = "users",
        operation: String = "INSERT",
        rowId: UUID = persistUser().id!!
    ): AuditLog = auditLogRepository.save(
        AuditLog(
            tableName = tableName,
            operation = operation,
            rowId = rowId,
            oldValues = null,
            newValues = """{"status":"created"}""",
            changedBy = persistUser(),
            changedAt = Instant.now()
        )
    )

    protected fun randomSuffix(): String = UUID.randomUUID().toString().substring(0, 8)
}

