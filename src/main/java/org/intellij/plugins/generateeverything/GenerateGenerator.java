package org.intellij.plugins.generateeverything;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static org.intellij.plugins.generateeverything.GenerateUtils.toLowerSnakeCase;
import static org.intellij.plugins.generateeverything.GenerateUtils.toUpperSnakeCase;

public class GenerateGenerator implements Runnable {

    private final Project project;

    private final PsiFile file;

    private final Editor editor;

    private final List<PsiFieldMember> selectedFields;

    private final PsiElementFactory psiElementFactory;

    public static void generate(final Project project,
                                final Editor editor,
                                final PsiFile psiFile,
                                final List<PsiFieldMember> selectedFields) {
        final Runnable genGen = new GenerateGenerator(project, psiFile, editor, selectedFields);
        ApplicationManager.getApplication().runWriteAction(genGen);
    }

    private GenerateGenerator(final Project project,
                              final PsiFile file,
                              final Editor editor,
                              final List<PsiFieldMember> selectedFields) {
        this.project = project;
        this.file = file;
        this.editor = editor;
        this.selectedFields = selectedFields;
        psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    }

    @Override public void run() {
        final PsiClass targetClass = GenerateUtils.getStaticOrTopLevelClass(file, editor);
        if (targetClass == null) {
            return;
        }
        final Set<GenerateOption> options = currentOptions();

        PsiElement lastAddedElement = null;

        if (options.contains(GenerateOption.SUPER_CONSTRUCTOR)) {
            lastAddedElement = addMethod(targetClass,
                                         null,
                                         generateSuperConstructor(targetClass),
                                         true);
        }

        if (options.contains(GenerateOption.EMPTY_CONSTRUCTOR)) {
            lastAddedElement = addMethod(targetClass,
                                         lastAddedElement,
                                         generateEmptyConstructor(targetClass),
                                         true);
        }

        if (options.contains(GenerateOption.ALL_ARGS_CONSTRUCTOR)) {
            lastAddedElement = addMethod(targetClass,
                                         lastAddedElement,
                                         generateConstructor(targetClass),
                                         true);
        }

        PsiField[] fields = targetClass.getFields();
        List<PsiField> psiFields = Arrays.asList(fields);
        Iterator<PsiField> iterator = psiFields.iterator();
        while (iterator.hasNext()) {
            PsiField field = iterator.next();
            if (options.contains(GenerateOption.GETTERS)) {
                lastAddedElement = addMethod(targetClass, lastAddedElement, addGetter(field), true);
            }

            if (options.contains(GenerateOption.SETTERS)) {
                lastAddedElement = addMethod(targetClass, lastAddedElement, addSetter(field), true);
            }
        }

        if (options.contains(GenerateOption.TO_STRING)) {
            addMethod(targetClass, lastAddedElement, addToString(targetClass), true);
        }

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file);
        CodeStyleManager.getInstance(project).reformat(targetClass);
    }

    private PsiMethod addToString(PsiClass targetClass) {
        PsiClassType stringClassType =
                psiElementFactory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING);
        PsiMethod toStringMethod = psiElementFactory.createMethod("toString", stringClassType);
        PsiModifierList modifierList = toStringMethod.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        modifierList.addAnnotation("java.lang.Override");

        // This tostring method needs to contain :
        // return "<class name>{"
        StringBuilder assignText = new StringBuilder("return \"" + targetClass.getName() + "{");

        // If there's a super method
        // {<super.toString()>},<space>
        if (targetClass.getSuperClass() != null
            && targetClass.getSuperClass().getFields().length > 0) {
            assignText.append("{\" + super.toString() + " + "\"}, ");
        }

        // If there's fields in the class, assign each one, otherwise just end with }";
        if (targetClass.getFields().length > 0) {

            // This needs to assign each field as name=value with single quotes if it's a string
            // name='bob', age=200, isFat=true, dob=1900-01-01T03:50:12.0000000T
            for (PsiField field : targetClass.getFields()) {
                assignText.append("\"\n + \"")
                          .append(field.getName())
                          .append("=")
                          .append(field.getType()
                                       .getCanonicalText()
                                       .equals(CommonClassNames.JAVA_LANG_STRING) ? "\'" : "")
                          .append("\" + ")
                          .append(field.getName())
                          .append(" + \"")
                          .append(field.getType()
                                       .getCanonicalText()
                                       .equals(CommonClassNames.JAVA_LANG_STRING) ? "\'" : "")
                          .append(", ");
            }
            // Delete the last two chars which should be <comma><space>
            assignText.deleteCharAt(assignText.length() - 1);
            assignText.deleteCharAt(assignText.length() - 1);

            // Then add the terminating brace and semi-colon
            assignText.append("}\";");
        }
        else {
            // Nothing in class so just add the brace and semi-colon
            assignText.append("+ \"}\";");
        }

        toStringMethod.getBody()
                      .add(psiElementFactory.createStatementFromText(assignText.toString(), null));
        return toStringMethod;
    }

    @Override public String toString() {
        return "GenerateGenerator{" + "project=" + project + ", file=" + file + ", editor=" + editor
               + ", selectedFields=" + selectedFields + ", psiElementFactory=" + psiElementFactory
               + '}';
    }

    private PsiMethod addSetter(PsiField field) {
        PsiMethod setMethod = psiElementFactory.createMethod(
                "set" + toUpperSnakeCase(field.getName()), PsiType.VOID);
        setMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        setMethod.getParameterList()
                 .add(psiElementFactory.createParameter(toLowerSnakeCase(field.getName()),
                                                        field.getType()));
        String assignText = "this." + toLowerSnakeCase(field.getName()) + " = " + toLowerSnakeCase(
                field.getName()) + ";";
        setMethod.getBody().add(psiElementFactory.createStatementFromText(assignText, null));
        return setMethod;
    }

    private PsiMethod addGetter(PsiField field) {
        PsiMethod getMethod = psiElementFactory.createMethod(
                "get" + toUpperSnakeCase(field.getName()), field.getType());
        getMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        String assignText = "return this." + toLowerSnakeCase(field.getName()) + ";";
        getMethod.getBody().add(psiElementFactory.createStatementFromText(assignText, null));
        return getMethod;
    }

    private PsiMethod generateEmptyConstructor(final PsiClass targetClass) {
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        return constructor;
    }

    private PsiElement addMethod(@NotNull final PsiClass target,
                                 @Nullable final PsiElement after,
                                 @NotNull final PsiMethod newMethod,
                                 final boolean replace) {
        PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);
        if (existingMethod == null && newMethod.isConstructor()) {
            for (final PsiMethod constructor : target.getConstructors()) {
                if (GenerateUtils.areParameterListsEqual(constructor.getParameterList(),
                                                         newMethod.getParameterList())) {
                    existingMethod = constructor;
                    break;
                }
            }
        }
        if (existingMethod == null) {
            if (after != null) {
                return target.addAfter(newMethod, after);
            }
            else {
                return target.add(newMethod);
            }
        }
        else if (replace) {
            existingMethod.replace(newMethod);
        }
        return existingMethod;
    }

    private static EnumSet<GenerateOption> currentOptions() {
        final EnumSet<GenerateOption> options = EnumSet.noneOf(GenerateOption.class);
        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        for (final GenerateOption option : GenerateOption.values()) {
            final boolean currentSetting = propertiesComponent.getBoolean(option.getProperty(),
                                                                          false);
            if (currentSetting) {
                options.add(option);
            }
        }
        return options;
    }

    private PsiMethod generateSuperConstructor(final PsiClass targetClass) {
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);

        List<PsiMethod> psiMethods = Arrays.asList(targetClass.getSuperClass().getConstructors());
        psiMethods.sort(Comparator.comparingInt(o -> o.getParameterList().getParametersCount()));
        PsiMethod longestSuperCon = psiMethods.get(psiMethods.size() - 1);
        PsiParameterList parameterList = longestSuperCon.getParameterList();

        List<PsiParameter> constructorParams = Arrays.stream(parameterList.getParameters()).filter(
                pl -> !pl.hasModifier(JvmModifier.PRIVATE)).collect(Collectors.toList());
        constructorParams.forEach(cp -> constructor.getParameterList().add(cp));

        String superText =
                "super(" + constructorParams.stream().map(PsiNamedElement::getName).collect(
                        Collectors.joining(", ")) + ");";
        constructor.getBody().add(psiElementFactory.createStatementFromText(superText, null));
        return constructor;
    }

    /**
     * Generate an all args constructor for the target class.
     *
     * @param targetClass the target class to operate on.
     *
     * @return the all args constructor.
     */
    private PsiMethod generateConstructor(final PsiClass targetClass) {
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        PsiCodeBlock conBody = constructor.getBody();

        PsiClass superClass = targetClass.getSuperClass();
        if (superClass != null) {
            List<PsiMethod> psiMethods = Arrays.asList(superClass.getConstructors());
            psiMethods.sort(Comparator.comparingInt(o -> o.getParameterList()
                                                          .getParametersCount()));
            PsiMethod longestSuperCon = psiMethods.get(psiMethods.size() - 1);
            PsiParameterList parameterList = longestSuperCon.getParameterList();

            List<PsiParameter> constructorParams =
                    Arrays.stream(parameterList.getParameters()).filter(pl -> !pl.hasModifier(
                            JvmModifier.PRIVATE)).collect(Collectors.toList());
            constructorParams.forEach(cp -> constructor.getParameterList().add(cp));

            String superText =
                    "super(" + constructorParams.stream().map(PsiNamedElement::getName).collect(
                            Collectors.joining(", ")) + ");";
            conBody.add(psiElementFactory.createStatementFromText(superText, null));
        }

        for (final PsiFieldMember fieldMember : selectedFields) {
            final PsiField field = fieldMember.getElement();

            final PsiParameter conParam =
                    psiElementFactory.createParameter(toLowerSnakeCase(field.getName()),
                                                      field.getType());
            constructor.getParameterList().add(conParam);
        }

        for (PsiFieldMember fieldMember : selectedFields) {
            final PsiField field = fieldMember.getElement();

            final String assignText =
                    "this." + field.getName() + " = " + toLowerSnakeCase(field.getName()) + ";";
            conBody.add(psiElementFactory.createStatementFromText(assignText, null));
        }

        return constructor;
    }
}
