package cn.iocoder.yudao.module.pms.platform.businessview;

import cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewRegistration;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.ControlledBusinessViewCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.businessview.BusinessViewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** PM-03 / F-PLT-003 AC-02, AC-03: immutable revisions, rejected transitions and expectedVersion conflicts. */
class BusinessViewRegistrationTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-08T10:00:00Z");
    private static final Instant DISABLED_AT = PUBLISHED_AT.plusSeconds(60);

    @Test
    void draftEditPublishDisableKeepsExactHistoricalContentAndOldSnapshots() {
        var original = page();
        var draft = BusinessViewRegistration.draft(original);
        assertEquals(1, draft.version());
        assertTrue(draft.revision(1, 1).isDraft());
        assertNull(draft.revision(1, 1).publishedAt());
        assertNull(draft.revision(1, 1).disabledAt());
        assertThrows(IllegalArgumentException.class, () -> draft.forNewReference(1, 1));

        var replacement = view(c -> c.supported = actions("VIEW"));
        var edited = draft.editDraft(replacement, 1);
        assertEquals(2, edited.version());
        assertEquals(original, draft.revision(1, 1).descriptor());
        assertEquals(replacement, edited.revision(1, 1).descriptor());
        var published = edited.publish(1, 1, 2, catalog(), PUBLISHED_AT);
        assertEquals(3, published.version());
        assertFalse(published.revision(1, 1).isDraft());
        assertEquals(PUBLISHED_AT, published.revision(1, 1).publishedAt());
        assertSame(published.revision(1, 1), published.forNewReference(1, 1));
        assertTrue(edited.revision(1, 1).isDraft());

        var disabled = published.disable(1, 1, 3, DISABLED_AT);
        assertEquals(4, disabled.version());
        assertFalse(disabled.revision(1, 1).isDraft());
        assertFalse(disabled.revision(1, 1).availableForNewReference());
        assertThrows(IllegalArgumentException.class, () -> disabled.forNewReference(1, 1));
        assertEquals(replacement, disabled.revision(1, 1).descriptor());
        assertEquals(PUBLISHED_AT, disabled.revision(1, 1).publishedAt());
        assertEquals(DISABLED_AT, disabled.revision(1, 1).disabledAt());
        assertNull(published.revision(1, 1).disabledAt());
        assertEquals(replacement, published.forNewReference(1, 1).descriptor());
    }

    @Test
    void publishedAndDisabledContentCannotBeEditedRepublishedOrDisabledAgain() {
        var draft = BusinessViewRegistration.draft(page());
        var published = draft.publish(1, 1, 1, catalog(), PUBLISHED_AT);
        var disabled = published.disable(1, 1, 2, DISABLED_AT);
        assertThrows(IllegalArgumentException.class, () -> published.editDraft(page(), 2));
        assertThrows(IllegalArgumentException.class, () -> disabled.editDraft(page(), 3));
        assertThrows(IllegalArgumentException.class, () -> published.publish(1, 1, 2, catalog(), PUBLISHED_AT));
        assertThrows(IllegalArgumentException.class, () -> disabled.publish(1, 1, 3, catalog(), PUBLISHED_AT));
        assertThrows(IllegalArgumentException.class, () -> disabled.disable(1, 1, 3, DISABLED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> draft.disable(1, 1, 1, DISABLED_AT));
        assertThrows(IllegalArgumentException.class, () -> published.disable(1, 1, 2, PUBLISHED_AT.minusSeconds(1)));
        assertEquals(DISABLED_AT, disabled.revision(1, 1).disabledAt());
        assertEquals(page(), disabled.revision(1, 1).descriptor());
    }

    @Test
    void copiesDraftPublishedOrDisabledSourceWithoutChangingOriginalRevision() {
        var draft = BusinessViewRegistration.draft(page());
        var published = draft.publish(1, 1, 1, catalog(), PUBLISHED_AT);
        var disabled = published.disable(1, 1, 2, DISABLED_AT);
        for (var source : List.of(draft, published, disabled)) {
            var copy = source.copyDraft(1, 1, 2, source.version());
            assertEquals(source.version() + 1, copy.version());
            assertEquals(page().withRevision(2), copy.revision(1, 2).descriptor());
            assertTrue(copy.revision(1, 2).isDraft());
            assertNull(copy.revision(1, 2).publishedAt());
            assertNull(copy.revision(1, 2).disabledAt());
            assertSame(source.revision(1, 1), copy.revision(1, 1));
            assertThrows(IllegalArgumentException.class, () -> source.revision(1, 2));
            assertThrows(IllegalArgumentException.class, () -> copy.copyDraft(1, 1, 2, copy.version()));
            assertThrows(IllegalArgumentException.class, () -> copy.copyDraft(1, 1, 1, copy.version()));
        }
    }

    @Test
    void publishingANewRevisionDoesNotReactivateOrRewriteDisabledHistory() {
        var published = BusinessViewRegistration.draft(page()).publish(1, 1, 1, catalog(), PUBLISHED_AT);
        var disabled = published.disable(1, 1, 2, DISABLED_AT);
        var copied = disabled.copyDraft(1, 1, 2, 3);
        var edited = copied.editDraft(view(c -> {
            c.revision = 2;
            c.supported = actions("VIEW");
        }), 4);
        var next = edited.publish(1, 2, 5, catalog(), DISABLED_AT.plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> next.forNewReference(1, 1));
        assertEquals(actions("VIEW", "EDIT"), next.revision(1, 1).descriptor().supportedActions());
        assertEquals(DISABLED_AT, next.revision(1, 1).disabledAt());
        assertEquals(actions("VIEW"), next.forNewReference(1, 2).descriptor().supportedActions());
        assertEquals(page(), next.revision(1, 1).descriptor());
    }

    @Test
    void rejectsStaleExpectedVersionForEveryMutationWithoutChangingAnySnapshot() {
        var draft = BusinessViewRegistration.draft(page());
        var edited = draft.editDraft(view(c -> c.supported = actions("VIEW")), 1);
        assertThrows(IllegalStateException.class, () -> edited.editDraft(page(), 1));
        assertThrows(IllegalStateException.class, () -> edited.copyDraft(1, 1, 2, 1));
        assertThrows(IllegalStateException.class, () -> edited.publish(1, 1, 1, catalog(), PUBLISHED_AT));
        var published = edited.publish(1, 1, 2, catalog(), PUBLISHED_AT);
        assertThrows(IllegalStateException.class, () -> published.disable(1, 1, 2, DISABLED_AT));
        assertEquals(2, edited.version());
        assertTrue(edited.revision(1, 1).isDraft());
        assertEquals(3, published.version());
        assertNull(published.revision(1, 1).disabledAt());
        assertEquals(actions("VIEW"), published.revision(1, 1).descriptor().supportedActions());
    }

    @Test
    void failedPublishPreservesDraftAndCanBeRetriedWithSuppliedValidFacts() {
        var draft = BusinessViewRegistration.draft(page());
        var noProviders = new ControlledBusinessViewCatalog(List.of(component()), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> draft.publish(1, 1, 1, noProviders, PUBLISHED_AT));
        assertThrows(IllegalArgumentException.class, () -> draft.publish(1, 1, 1, null, PUBLISHED_AT));
        assertThrows(NullPointerException.class, () -> draft.publish(1, 1, 1, catalog(), null));
        assertEquals(1, draft.version());
        assertTrue(draft.revision(1, 1).isDraft());
        var published = draft.publish(1, 1, 1, catalog(), PUBLISHED_AT);
        assertTrue(published.forNewReference(1, 1).availableForNewReference());
        // Historical access never requires a current catalog or a runtime Provider health check.
        assertEquals(page(), published.revision(1, 1).descriptor());
    }

    @Test
    void formPublicationNeedsAvailableExactRevisionAndPreservesFormConfigurationOnCopy() {
        var draft = BusinessViewRegistration.draft(form());
        assertThrows(IllegalArgumentException.class, () -> draft.publish(1, 1, 1, formCatalog(2, true), PUBLISHED_AT));
        assertThrows(IllegalArgumentException.class, () -> draft.publish(1, 1, 1, formCatalog(1, false), PUBLISHED_AT));
        assertTrue(draft.revision(1, 1).isDraft());
        var published = draft.publish(1, 1, 1, formCatalog(1, true), PUBLISHED_AT);
        var copy = published.copyDraft(1, 1, 2, 2);
        assertEquals(form().withRevision(2), copy.revision(1, 2).descriptor());
        assertEquals(91L, copy.revision(1, 2).descriptor().dynamicFormRevisionId());
        assertEquals(form(), copy.forNewReference(1, 1).descriptor());
    }

    @Test
    void identityOwnerTenantAndMissingExactRevisionsAreRejected() {
        var draft = BusinessViewRegistration.draft(page());
        assertThrows(IllegalArgumentException.class, () -> draft.editDraft(view(c -> c.owner = "ACC"), 1));
        assertThrows(IllegalArgumentException.class, () -> draft.editDraft(view(c -> c.entity = "ACC.Report"), 1));
        assertThrows(IllegalArgumentException.class, () -> draft.editDraft(view(c -> c.viewKey = "SOL.Other"), 1));
        assertThrows(IllegalArgumentException.class, () -> draft.editDraft(view(c -> c.tenant = 2), 1));
        assertThrows(IllegalArgumentException.class, () -> draft.editDraft(page().withRevision(2), 1));
        assertThrows(IllegalArgumentException.class, () -> draft.revision(2, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.forNewReference(2, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.copyDraft(2, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.publish(2, 1, 1, catalog(), PUBLISHED_AT));
        assertThrows(IllegalArgumentException.class, () -> draft.revision(1, 2));
        assertThrows(IllegalArgumentException.class, () -> draft.copyDraft(1, 2, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.copyDraft(1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.publish(1, 2, 1, catalog(), PUBLISHED_AT));
        var published = draft.publish(1, 1, 1, catalog(), PUBLISHED_AT);
        assertThrows(IllegalArgumentException.class, () -> published.disable(2, 1, 2, DISABLED_AT));
        assertThrows(IllegalArgumentException.class, () -> published.disable(1, 2, 2, DISABLED_AT));
        assertEquals(page(), draft.revision(1, 1).descriptor());
    }

    @Test
    void externalJsonMutationsCannotRewritePublishedOrCopiedHistory() {
        ObjectNode inputSchema = (ObjectNode) schema();
        ArrayNode inputActions = (ArrayNode) actions("VIEW", "EDIT");
        var descriptor = view(c -> {
            c.context = inputSchema;
            c.supported = inputActions;
        });
        var published = BusinessViewRegistration.draft(descriptor).publish(1, 1, 1, catalog(), PUBLISHED_AT);
        var copy = published.copyDraft(1, 1, 2, 2);
        inputSchema.put("injected", true);
        inputActions.add("DELETE");
        ((ObjectNode) published.revision(1, 1).descriptor().contextSchema()).put("injectedAgain", true);
        ((ArrayNode) published.revision(1, 1).descriptor().supportedActions()).add("ADMIN");
        ((ObjectNode) copy.revision(1, 2).descriptor().contextSchema()).put("copyInjection", true);
        assertEquals(page(), published.revision(1, 1).descriptor());
        assertEquals(page(), copy.revision(1, 1).descriptor());
        assertEquals(page().withRevision(2), copy.revision(1, 2).descriptor());
        assertEquals(PUBLISHED_AT, published.revision(1, 1).publishedAt());
    }
}
