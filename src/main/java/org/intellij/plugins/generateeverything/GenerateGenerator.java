package org.intellij.plugins.generateeverything;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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

    private static final Logger LOGGER = Logger.getInstance(GenerateGenerator.class);

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

    @Override
    public void run() {
        final PsiClass targetClass = GenerateUtils.getStaticOrTopLevelClass(file, editor);
        if (targetClass == null) {
            return;
        }
        final Set<GenerateOption> options = currentOptions();

        String targetClassName = targetClass.getName();
        LOGGER.trace("Operating on class : " + targetClassName +".");

        PsiElement lastAddedElement = null;

        if (options.contains(GenerateOption.EMPTY_CONSTRUCTOR)) {
            LOGGER.trace("Adding empty constructor.");

            lastAddedElement = addMethod(targetClass,
                                         null,
                                         generateEmptyConstructor(targetClass),
                                         true);
        }

        if (options.contains(GenerateOption.SUPER_ARGS_CONSTRUCTOR)) {
            LOGGER.trace("Adding super constructor.");

            PsiMethod superConstructor = generateSuperConstructor(targetClass);
            if (superConstructor != null) {
                LOGGER.trace("Has super - adding constructor : " + superConstructor + ".");
                lastAddedElement = addMethod(targetClass,
                                             lastAddedElement,
                                             superConstructor,
                                             true);
            } else {
                LOGGER.trace("Super constructor returned null - skipping.");
            }
        }

        if (options.contains(GenerateOption.SUPER_OBJECT_CONSTRUCTOR)) {
            LOGGER.trace("Adding super object constructor.");

            PsiMethod superObjConstructor = generateSuperObjectConstructor(targetClass);
            if (superObjConstructor != null) {
                LOGGER.trace("Has super - adding object constructor : " + superObjConstructor + ".");
                lastAddedElement = addMethod(targetClass,
                                             lastAddedElement,
                                             superObjConstructor,
                                             true);
            } else {
                LOGGER.trace("Super object constructor returned null - skipping.");
            }
        }

        if (options.contains(GenerateOption.ALL_ARGS_CONSTRUCTOR)) {
            LOGGER.trace("Adding all args constructor.");

            PsiMethod allArgsConstructor = genAllArgsConstr(targetClass);
            if (allArgsConstructor != null) {
                LOGGER.trace("Has all args - adding constructor : " + allArgsConstructor + ".");
                lastAddedElement = addMethod(targetClass,
                                             lastAddedElement,
                                             allArgsConstructor,
                                             true);
            } else {
                LOGGER.trace("All args constructor returned null - skipping.");
            }
        }

        if (options.contains(GenerateOption.ALL_ARGS_SUPER_CONSTRUCTOR)
            && targetClass.getSuperClass() != null
            && !CommonClassNames.JAVA_LANG_OBJECT.equals(targetClass.getSuperClass().getQualifiedName())) {

            LOGGER.info("Generating all args super constructor(s).");

            PsiMethod allArgsSuperConstructor = generateAllArgsSuperConstructor(targetClass);
            if (allArgsSuperConstructor != null) {
                LOGGER.trace("Has all args super - adding constructor : " + allArgsSuperConstructor + ".");
                try {
                    lastAddedElement = addMethod(targetClass, lastAddedElement, allArgsSuperConstructor, true);
                } catch (Exception e) {
                    LOGGER.error("Exception thrown : " + e);
                    e.printStackTrace();
                }
            } else {
                LOGGER.trace("All args super constructor returned null - skipping.");
            }
        }

        PsiField[] fields = targetClass.getFields();
        for (PsiField field : fields) {
            LOGGER.trace("Adding get/set for : "+field.getName()+".");

            if (options.contains(GenerateOption.GETTERS)) {
                lastAddedElement = addMethod(targetClass, lastAddedElement, generateGetter(field), true);
            }

            if (options.contains(GenerateOption.SETTERS)) {
                lastAddedElement = addMethod(targetClass, lastAddedElement, generateSetter(field), true);
            }
        }

        if (options.contains(GenerateOption.TO_STRING)) {
            LOGGER.trace("Adding tostring.");
            addMethod(targetClass, lastAddedElement, addToString(targetClass), true);
        }

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file);
        CodeStyleManager.getInstance(project).reformat(targetClass);

        LOGGER.trace("Generation complete for class : "+targetClassName+".");
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

    @Override
    public String toString() {
        return "GenerateGenerator{" + "project=" + project + ", file=" + file + ", editor=" + editor
               + ", selectedFields=" + selectedFields + ", psiElementFactory=" + psiElementFactory
               + '}';
    }

    /**
     * Add a single setter for a field.
     *
     * @param field the field to add the setter for.
     * @return a setter taking the argument with the field's type and setting this.field.
     */
    private PsiMethod generateSetter(PsiField field) {
        LOGGER.trace("Generating setter for : " + field.getName());

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

    /**
     * Add a single getter for a field.
     *
     * @param field the field to add the getter for.
     * @return a getter returning this.field.
     */
    private PsiMethod generateGetter(PsiField field) {
        LOGGER.trace("Generating getter for : " + field.getName());

        PsiMethod getMethod = psiElementFactory.createMethod(
                "get" + toUpperSnakeCase(field.getName()), field.getType());
        getMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        String assignText = "return this." + toLowerSnakeCase(field.getName()) + ";";
        getMethod.getBody().add(psiElementFactory.createStatementFromText(assignText, null));
        return getMethod;
    }

    /**
     * Add a method to the existing PSI Tree. If this has an existing method overwrite it, if there is no existing
     * method, create a new one, placing it after the parameter after otherwise just adding it.
     *
     * @param target the target class to operate on.
     * @param after the target element to attempt to add the new method after (either an existing element or null).
     * @param newMethod the new method to add.
     * @param replace whether or not to replace existing elements, if this is false and a method already exists, the
     *                new method WILL NOT be added.
     *
     * @return the element as modified.
     */
    private PsiElement addMethod(@NotNull final PsiClass target,
                                 @Nullable final PsiElement after,
                                 @NotNull final PsiMethod newMethod,
                                 final boolean replace) {
        LOGGER.trace("Adding method to target : " + target.getName());

        // Get the existing method if it exists.
        PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);

        // If there's no existing method and the new method is a constructor, set this to be the constructor
        if (existingMethod == null && newMethod.isConstructor()) {
            for (final PsiMethod constructor : target.getConstructors()) {
                if (GenerateUtils.areParameterListsEqual(constructor.getParameterList(),
                                                         newMethod.getParameterList())) {
                    existingMethod = constructor;
                    break;
                }
            }
        }

        // If the existing method is null, add this method after the after element, if there's no after element just
        // add it to the tree.
        if (existingMethod == null) {
            if (after != null) {
                return target.addAfter(newMethod, after);
            }
            else {
                return target.add(newMethod);
            }
        }
        // Otherwise replace the method
        else if (replace) {
            existingMethod.replace(newMethod);
        }

        // Return the new tree
        return existingMethod;
    }

    /**
     * Get enable options for generation.
     * @return a list of the enums that have been set.
     */
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

    /**
     * Generate an empty constructor with no arguments and no super to allow new class();.
     *
     * @param targetClass the target class to generate for.
     * @return the empty constructor.
     */
    private PsiMethod generateEmptyConstructor(final PsiClass targetClass) {
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        LOGGER.trace("Adding constructor :\r\n" + constructor.getText());
        return constructor;
    }

    /**
     * Generate a super constructor with no other arguments.
     *
     * @param targetClass the target class to generate a super constructor for.
     * @return the super constructor all args method.
     */
    private PsiMethod generateSuperConstructor(final PsiClass targetClass) {
        // Initial sanity checks
        if (targetClass == null
            || targetClass.getName() == null
            || targetClass.getSuperClass() == null) {
            LOGGER.error("Failed to generate super, targetClass, class name or superclass is null " + targetClass);
            return null;
        }

        LOGGER.trace("Generating a super constructor for : " + targetClass.getName());

        // Create the constructor method and mark it as private?
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

        // Get the super class' fields
        List<PsiMethod> psiMethods = Arrays.asList(targetClass.getSuperClass().getConstructors());

        // Get the largest one (hopefully all fields)
        psiMethods.sort(Comparator.comparingInt(o -> o.getParameterList().getParametersCount()));

        // Create a list of constructor parameters to add to for our constructor
        List<PsiParameter> constructorParams = new ArrayList<>();

        // If there are super class constructor fields to add, do it
        if (!psiMethods.isEmpty()) {
            // Get the largest constuctor
            PsiMethod longestSuperCon = psiMethods.get(psiMethods.size() - 1);
            // Get the parameter list
            PsiParameterList parameterList = longestSuperCon.getParameterList();

            // If the fields aren't marked private, add them to our constructor as parameters
            constructorParams = Arrays.stream(parameterList.getParameters())
                                      .filter(pl -> !pl.hasModifier(JvmModifier.PRIVATE))
                                      .collect(Collectors.toList());
            constructorParams.forEach(cp -> constructor.getParameterList().add(cp));
        } else {
            // If there are no super constructor parameters just add an empty super
            LOGGER.trace("Super has no visible methods or constructors v0v.");
        }

        // Create the super constructor and add it to the body before adding local parameters to constructor
        String superText =
                "super(" + constructorParams.stream()
                                            .map(PsiNamedElement::getName)
                                            .collect(Collectors.joining(", ")) + ");";
        if (constructor.getBody() == null) {
            LOGGER.error("Failed to get null constructor body!");
            return null;
        }
        constructor.getBody().add(psiElementFactory.createStatementFromText(superText, null));

        LOGGER.trace("Adding constructor :\r\n" + constructor.getText());

        return constructor;
    }

    /**
     * Generate a super constructor as a single object with no other arguments.
     *
     * @param targetClass the target class to generate a super constructor for.
     * @return the super constructor all args method.
     */
    private PsiMethod generateSuperObjectConstructor(final PsiClass targetClass) {
        // Initial sanity checks
        if (targetClass == null
            || targetClass.getName() == null
            || targetClass.getSuperClass() == null
            || targetClass.getSuperClass().getName() == null) {
            LOGGER.error("Failed to generate super, targetClass, class name or superclass is null " + targetClass);
            return null;
        }
        PsiClass superClass = targetClass.getSuperClass();

        LOGGER.trace("Generating a single object super constructor for : " + targetClass.getName());

        // If there are no object superclass constructors then don't attempt this
        boolean hasObjectConstructor = Arrays.stream(superClass.getConstructors())
                                             .map(PsiMethod::getParameterList)
                                             .filter(pl -> pl.getParameters().length > 0)
                                             .map(pl -> pl.getParameter(0))
                                             .filter(Objects::nonNull)
                                             .map(p -> p.getType().getCanonicalText())
                                             .anyMatch(ct -> ct.equals(superClass.getQualifiedName()));
        if (!hasObjectConstructor) {
            LOGGER.trace("No object superclass constructor found, exiting.");
            return null;
        }

        // Create the constructor method and mark it as public
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

        // Get the super class
        if (superClass.getName() == null) {
            LOGGER.error("Failed to get super class.");
            return null;
        }

        final PsiParameter conParam =
                psiElementFactory.createParameter(toLowerSnakeCase(superClass.getName()),
                                                  targetClass.getSuperTypes()[0]);
        constructor.getParameterList().add(conParam);

        // Add the single super class parameter
        String superText =
                "super(" + toLowerSnakeCase(superClass.getName()) + ");";
        if (constructor.getBody() == null) {
            LOGGER.error("Failed to get null constructor body!");
            return null;
        }
        constructor.getBody().add(psiElementFactory.createStatementFromText(superText, null));

        LOGGER.trace("Adding constructor :\r\n" + constructor.getText());

        return constructor;
    }

    /**
     * Generate a super constructor with all arguments.
     *
     * @param targetClass the target class to generate a super constructor for.
     * @return the super constructor all args method.
     */
    private PsiMethod generateAllArgsSuperConstructor(final PsiClass targetClass) {
        // Initial sanity checks
        if (targetClass == null
            || targetClass.getName() == null
            || targetClass.getSuperClass() == null) {
            LOGGER.error("Failed to generate super, targetClass, class name or superclass is null " + targetClass);
            return null;
        }

        LOGGER.trace("Generating all args super constructor for : " + targetClass.getName());

        // Create the constructor method and mark it as public
        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

        // Get the super class' fields
        List<PsiMethod> psiMethods = Arrays.asList(targetClass.getSuperClass().getConstructors());

        // Get the largest one (hopefully all fields)
        psiMethods.sort(Comparator.comparingInt(o -> o.getParameterList().getParametersCount()));

        // Create a list of constructor parameters to add to for our constructor
        List<PsiParameter> constructorParams = new ArrayList<>();

        // If there are super class constructor fields to add, do it
        if (!psiMethods.isEmpty()) {
            // Get the largest constuctor
            PsiMethod longestSuperCon = psiMethods.get(psiMethods.size() - 1);
            // Get the parameter list
            PsiParameterList parameterList = longestSuperCon.getParameterList();

            // If the fields aren't marked private, add them to our constructor as parameters
            constructorParams = Arrays.stream(parameterList.getParameters())
                                      .filter(pl -> !pl.hasModifier(JvmModifier.PRIVATE))
                                      .collect(Collectors.toList());
            constructorParams.forEach(cp -> constructor.getParameterList().add(cp));
        } else {
            // If there are no super constructor parameters just add an empty super
            LOGGER.trace("Super has no visible methods or constructors v0v.");
        }

        // Create the super constructor and add it to the body before adding local parameters to constructor
        String superText =
                "super(" + constructorParams.stream()
                                            .map(PsiNamedElement::getName)
                                            .collect(Collectors.joining(", ")) + ");";
        if (constructor.getBody() == null) {
            LOGGER.error("Failed to get null constructor body!");
            return null;
        }
        constructor.getBody().add(psiElementFactory.createStatementFromText(superText, null));

        // Loop through local class fields and add them as params
        for (final PsiFieldMember fieldMember : selectedFields) {
            final PsiField field = fieldMember.getElement();

            // Create the param and add it to the parameter list
            final PsiParameter conParam =
                    psiElementFactory.createParameter(toLowerSnakeCase(field.getName()),
                                                      field.getType());
            constructor.getParameterList().add(conParam);
        }

        // Add the local set statements for local fields
        for (PsiFieldMember fieldMember : selectedFields) {
            final PsiField field = fieldMember.getElement();

            final String assignText =
                    "this." + field.getName() + " = " + toLowerSnakeCase(field.getName()) + ";";
            constructor.getBody().add(psiElementFactory.createStatementFromText(assignText, null));
        }

        LOGGER.trace("Adding constructor :\r\n" + constructor.getText());

        return constructor;
    }

    /**
     * Generate an all args constructor for the target class.
     *
     * @param targetClass the target class to operate on.
     *
     * @return the all args constructor.
     */
    private PsiMethod genAllArgsConstr(final PsiClass targetClass) {
        // Initial sanity checks
        if (targetClass == null
            || targetClass.getName() == null) {
            LOGGER.error("Failed to generate all args, targetClass, class name is null " + targetClass);
            return null;
        }

        LOGGER.trace("Generating all args constructor for : " + targetClass.getName());

        final PsiMethod constructor = psiElementFactory.createConstructor(targetClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

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
            constructor.getBody().add(psiElementFactory.createStatementFromText(assignText, null));
        }

        LOGGER.trace("Adding constructor :\r\n" + constructor.getText());

        return constructor;
    }
}
