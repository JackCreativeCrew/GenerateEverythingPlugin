package org.intellij.plugins.generateeverything;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public final class GenerateUtils {
    @NonNls
    static final String JAVA_DOT_LANG = "java.lang.";


    /**
     * @param file   psi file
     * @param editor editor
     * @return psiClass if class is static or top level. Otherwise returns {@code null}
     */
    @Nullable
    public static PsiClass getStaticOrTopLevelClass(PsiFile file, Editor editor) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass != null && (psiClass.hasModifierProperty(PsiModifier.STATIC) ||
                                 psiClass.getManager().areElementsEquivalent(psiClass, topLevelClass)))
            return psiClass;
        else
            return null;
    }

    static boolean areParameterListsEqual(PsiParameterList paramList1, PsiParameterList paramList2) {
        if (paramList1.getParametersCount() != paramList2.getParametersCount()) {
            return false;
        }

        final PsiParameter[] param1Params = paramList1.getParameters();
        final PsiParameter[] param2Params = paramList2.getParameters();
        for (int i = 0; i < param1Params.length; i++) {
            final PsiParameter param1Param = param1Params[i];
            final PsiParameter param2Param = param2Params[i];

            if (!areTypesPresentableEqual(param1Param.getType(), param2Param.getType())) {
                return false;
            }
        }

        return true;
    }

    static boolean areTypesPresentableEqual(PsiType type1, PsiType type2) {
        if (type1 != null && type2 != null) {
            final String type1Canonical = stripJavaLang(type1.getPresentableText());
            final String type2Canonical = stripJavaLang(type2.getPresentableText());
            return type1Canonical.equals(type2Canonical);
        }

        return false;
    }

    static String stripJavaLang(String typeString) {
        return typeString.startsWith(JAVA_DOT_LANG) ? typeString.substring(JAVA_DOT_LANG.length()) : typeString;
    }

    public static String capitalize(String str) {
        return hasOneLetterPrefix(str) ?
               Character.toUpperCase(str.charAt(1)) + str.substring(2) :
               Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static boolean hasOneLetterPrefix(String str) {
        return Character.isLowerCase(str.charAt(0)) && Character.isUpperCase(str.charAt(1));
    }

    /**
     * Does the string have a lowercase character?
     *
     * @param str the string to test.
     * @return true if the string has a lowercase character, false if not.
     */
    public static boolean hasLowerCaseChar(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isLowerCase(str.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    public static String toLowerSnakeCase(final String str) {
        if (str.length() == 0)
            return "";

        if (Character.isUpperCase(str.charAt(0)))
            return (str.charAt(0) + "").toLowerCase() + str.substring(1);
        else return str;
    }

    public static String toUpperSnakeCase(final String str) {
        if (str.length() == 0)
            return "";

        if (Character.isLowerCase(str.charAt(0)))
            return (str.charAt(0) + "").toUpperCase() + str.substring(1);
        else return str;
    }
}
