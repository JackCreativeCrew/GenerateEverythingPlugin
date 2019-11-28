package org.intellij.plugins.generateeverything;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

public class GenerateOptionSelector {
    private static final List<SelectorOption> OPTIONS = createGeneratorOptions();

    private static List<SelectorOption> createGeneratorOptions() {
        final List<SelectorOption> options = new ArrayList<SelectorOption>(8);

        options.add(SelectorOption.newBuilder()
                                  .withCaption("Add an empty constructor")
                                  .withMnemonic('a')
                                  .withToolTip("Generate an empty constructor")
                                  .withOption(GenerateOption.EMPTY_CONSTRUCTOR)
                                  .build());
        options.add(SelectorOption.newBuilder()
                                  .withCaption("Add a super constructor")
                                  .withMnemonic('b')
                                  .withToolTip("Generate a super constructor")
                                  .withOption(GenerateOption.SUPER_CONSTRUCTOR)
                                  .build());
        options.add(SelectorOption.newBuilder()
                                  .withCaption("Add an all args constructor")
                                  .withMnemonic('c')
                                  .withToolTip("Generate an all args constructor")
                                  .withOption(GenerateOption.ALL_ARGS_CONSTRUCTOR)
                                  .build());
        options.add(SelectorOption.newBuilder()
                                  .withCaption("Generate getters")
                                  .withMnemonic('g')
                                  .withToolTip("Generate getters")
                                  .withOption(GenerateOption.GETTERS)
                                  .build());
        options.add(SelectorOption.newBuilder()
                                  .withCaption("Generate setters")
                                  .withMnemonic('s')
                                  .withToolTip("Generate setters")
                                  .withOption(GenerateOption.SETTERS)
                                  .build());
        options.add(SelectorOption.newBuilder()
                                  .withCaption("Generate a toString")
                                  .withMnemonic('t')
                                  .withToolTip("Generate  a toString")
                                  .withOption(GenerateOption.TO_STRING)
                                  .build());

        return options;
    }

    @Nullable
    public static List<PsiFieldMember> selectFieldsAndOptions(final List<PsiFieldMember> members,
                                                              final Project project) {
        if (members == null || members.isEmpty()) {
            return null;
        }

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return members;
        }

        final JCheckBox[] optionCheckBoxes = buildOptionCheckBoxes();

        final PsiFieldMember[] memberArray = members.toArray(new PsiFieldMember[members.size()]);

        final MemberChooser<PsiFieldMember> chooser = new MemberChooser<PsiFieldMember>(memberArray,
                                                                                        false,
// allowEmptySelection
                                                                                        true,
// allowMultiSelection
                                                                                        project,
                                                                                        null,
                                                                                        optionCheckBoxes);

        chooser.setTitle("Select Fields and Options for the Builder");
        chooser.selectElements(memberArray);
        if (chooser.showAndGet()) {
            return chooser.getSelectedElements();
        }

        return null;
    }

    private static JCheckBox[] buildOptionCheckBoxes() {
        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        final int optionCount = OPTIONS.size();
        final JCheckBox[] checkBoxesArray = new JCheckBox[optionCount];
        for (int i = 0; i < optionCount; i++) {
            checkBoxesArray[i] = buildOptionCheckBox(propertiesComponent, OPTIONS.get(i));
        }

        return checkBoxesArray;
    }

    private static JCheckBox buildOptionCheckBox(final PropertiesComponent propertiesComponent,
                                                 final SelectorOption selectorOption) {
        final GenerateOption option = selectorOption.getOption();

        final JCheckBox optionCheckBox = new NonFocusableCheckBox(selectorOption.getCaption());
        optionCheckBox.setMnemonic(selectorOption.getMnemonic());
        optionCheckBox.setToolTipText(selectorOption.getToolTip());

        final String optionProperty = option.getProperty();
        optionCheckBox.setSelected(propertiesComponent.isTrueValue(optionProperty));
        optionCheckBox.addItemListener(new ItemListener() {
            @Override public void itemStateChanged(final ItemEvent event) {
                propertiesComponent.setValue(optionProperty,
                                             Boolean.toString(optionCheckBox.isSelected()));
            }
        });
        return optionCheckBox;
    }
}
