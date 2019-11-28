package org.intellij.plugins.generateeverything;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class GenerateAction extends BaseCodeInsightAction {
    private final GenerateHandler handler = new GenerateHandler();

    @NotNull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return handler;
    }

    @Override
    protected boolean isValidForFile(@NotNull Project project,
                                     @NotNull Editor editor,
                                     @NotNull PsiFile file) {
        return handler.isValidFor(editor, file);
    }
}
