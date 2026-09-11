package cn.iocoder.yudao.module.pms.project.service.taskworkbench;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskRichTextTest {
    @Test void keepsFormattingAndMoreThanLegacyFiveHundredCharacters() {
        String input = "<p><strong>" + "任务说明".repeat(200) + "</strong></p>";
        String clean = TaskRichText.clean(input);
        assertTrue(clean.length() > 500); assertTrue(clean.contains("<strong>"));
    }
    @Test void removesExecutableMarkupAndDangerousLinks() {
        String clean = TaskRichText.clean("<p onclick='alert(1)'>说明<script>alert(1)</script><a href='javascript:alert(1)'>链接</a><img src='x' onerror='alert(1)'></p>");
        assertFalse(clean.contains("<script")); assertFalse(clean.contains("onclick")); assertFalse(clean.contains("onerror")); assertFalse(clean.contains("javascript:"));
    }
    @Test void enforcesTheSharedLimitAndAllowsEmptyDescription() {
        assertEquals("", TaskRichText.clean(null));
        assertThrows(RuntimeException.class, () -> TaskRichText.clean("x".repeat(TaskRichText.MAX_LENGTH + 1)));
    }
}
