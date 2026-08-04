package ru.kavader.arepos.dto

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.constraints.NotNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RequestBody
import ru.kavader.arepos.controller.AccessSharesController
import ru.kavader.arepos.controller.ComponentsController
import ru.kavader.arepos.controller.DiagramEditLocksController
import ru.kavader.arepos.controller.DiagramsController
import ru.kavader.arepos.controller.DocumentsController
import ru.kavader.arepos.controller.LinkTypesController
import ru.kavader.arepos.controller.LinksController
import ru.kavader.arepos.controller.ModelsController
import ru.kavader.arepos.controller.NodeShapesController
import ru.kavader.arepos.controller.NodeTypesController
import ru.kavader.arepos.controller.NodesController
import ru.kavader.arepos.controller.NotationsController
import ru.kavader.arepos.controller.PermissionsController
import ru.kavader.arepos.controller.RelationRulesController
import ru.kavader.arepos.controller.RelationRulesSyncController
import ru.kavader.arepos.controller.RelationsController
import ru.kavader.arepos.controller.UsersController
import ru.kavader.arepos.dto.model.DiagramShareLinkRequest
import ru.kavader.arepos.dto.model.DiagramUpdateRequest
import ru.kavader.arepos.dto.notation.ComponentRequest
import ru.kavader.arepos.dto.notation.LinkTypeRequest
import ru.kavader.arepos.dto.notation.NodeShapeRequest
import ru.kavader.arepos.dto.notation.NodeTypeRequest
import ru.kavader.arepos.dto.notation.RelationRequest
import ru.kavader.arepos.dto.system.RelationRuleSyncItem
import ru.kavader.arepos.dto.system.RelationRulesSyncRequest
import ru.kavader.arepos.dto.user.UserRequest
import java.util.UUID

class BeanValidationAuditTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `required text fields reject blanks and invalid email`() {
        val id = UUID.randomUUID()

        assertFalse(validator.validate(ComponentRequest("", "", id, nodeTypeId = id)).isEmpty())
        assertFalse(validator.validate(RelationRequest("", "", id, linkTypeId = id)).isEmpty())
        assertFalse(validator.validate(NodeTypeRequest(" ")).isEmpty())
        assertFalse(validator.validate(LinkTypeRequest("\t")).isEmpty())
        assertFalse(validator.validate(NodeShapeRequest("\n")).isEmpty())
        assertFalse(validator.validate(UserRequest("not-an-email")).isEmpty())
        // DiagramUpdateRequest fields are optional (partial update) — blank-as-absent is allowed
        assertTrue(validator.validate(DiagramUpdateRequest(name = null, version = null)).isEmpty())
    }

    @Test
    fun `diagram share link requires exactly one supported target`() {
        val id = UUID.randomUUID()

        assertFalse(validator.validate(DiagramShareLinkRequest()).isEmpty())
        assertTrue(validator.validate(DiagramShareLinkRequest(diagramId = id)).isEmpty())
        assertTrue(
            validator.validate(
                DiagramShareLinkRequest(modelId = id, diagramName = "Main", latest = true)
            ).isEmpty()
        )
        assertFalse(
            validator.validate(
                DiagramShareLinkRequest(diagramId = id, modelId = id, diagramName = "Main", latest = true)
            ).isEmpty()
        )
    }

    @Test
    fun `required uuid fields carry NotNull constraints`() {
        val requiredFields = mapOf(
            "ru.kavader.arepos.dto.model.LinkRequest" to
                listOf("sourceId", "targetId", "modelId"),
            "ru.kavader.arepos.dto.model.DiagramRequest" to listOf("notationId"),
            "ru.kavader.arepos.dto.document.RegisterDocumentRefRequest" to listOf("fileId"),
            "ru.kavader.arepos.dto.access.PermissionCheckRequest" to
                listOf("resourceType", "resourceId"),
            "ru.kavader.arepos.dto.access.AccessShareRequest" to
                listOf("resourceType", "resourceId"),
            RelationRuleSyncItem::class.java.name to
                listOf("fromComponentId", "toComponentId", "allowedRelationIds")
        )

        requiredFields.forEach { (className, fields) ->
            val type = Class.forName(className)
            fields.forEach { fieldName ->
                assertTrue(
                    type.getDeclaredField(fieldName).isAnnotationPresent(NotNull::class.java),
                    "$className.$fieldName must be @NotNull"
                )
            }
        }
        assertTrue(
            RelationRulesSyncRequest::class.java
                .getDeclaredField("rules")
                .isAnnotationPresent(Valid::class.java)
        )
    }

    @Test
    fun `typed request bodies are validated in audit controllers`() {
        val controllers = listOf(
            ComponentsController::class.java,
            RelationsController::class.java,
            NodeTypesController::class.java,
            LinkTypesController::class.java,
            LinksController::class.java,
            NodesController::class.java,
            DiagramsController::class.java,
            ModelsController::class.java,
            NotationsController::class.java,
            UsersController::class.java,
            AccessSharesController::class.java,
            RelationRulesController::class.java,
            RelationRulesSyncController::class.java,
            NodeShapesController::class.java,
            DocumentsController::class.java,
            PermissionsController::class.java,
            DiagramEditLocksController::class.java
        )

        controllers.flatMap { it.declaredMethods.toList() }.forEach { method ->
            method.parameters.forEach { parameter ->
                val isTypedBody =
                    parameter.isAnnotationPresent(RequestBody::class.java) &&
                        parameter.type != String::class.java &&
                        parameter.type != JsonNode::class.java
                if (isTypedBody) {
                    assertTrue(
                        parameter.isAnnotationPresent(Valid::class.java),
                        "${method.declaringClass.simpleName}.${method.name} request body must be @Valid"
                    )
                }
            }
        }
    }
}
