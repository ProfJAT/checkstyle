package com.cse123.checks.specs;

import java.util.*;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;

import net.sf.saxon.expr.parser.Token;

@StatelessCheck
public class FieldSpecificationCheck extends AbstractCheck {
    private Map<String, RequiredField> requiredFields;
    private String requiredFieldStrings;

    public static final String MSG_MALFORMED_FIELD = "malformed.field";

    public static final String MSG_MISSING_FIELD = "missing.field";

    public static final String[] PRIVATE_FIELD_EXCEPTIONS = new String[]{
        "ListNode", "AssassinNode", "IntTreeNode", "QuestionNode", "HuffmanNode" 
    };

    public FieldSpecificationCheck() {
        requiredFields = new HashMap<>();
        requiredFieldStrings = "";
    }

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.VARIABLE_DEF
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return CommonUtil.EMPTY_INT_ARRAY;
    }

    @Override
    public void visitToken(DetailAST ast) {
        if (ast.getParent().getParent().getType() == TokenTypes.CLASS_DEF &&
            ast.getParent().getType() != TokenTypes.METHOD_DEF) {
            RequiredField currField = new RequiredField(ast);

            // If name found in specification, log descrepencies
            if (requiredFields.keySet().contains(currField.getName())) {
                RequiredField specField = requiredFields.get(currField.getName());

                // Check visibility
                if (!specField.getVisibility().equals(currField.getVisibility())) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_FIELD, 
                        currField.getName(), currField.getVisibility(), specField.getVisibility());
                }
                // Check static
                if (!specField.getIsStatic() == currField.getIsStatic()) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_FIELD, 
                        currField.getName(), currField.getStaticAsString(), specField.getStaticAsString());
                }
                // Check final-ness
                if (!specField.getIsFinal() == currField.getIsFinal()) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_FIELD, 
                        currField.getName(), currField.getFinalAsString(), specField.getFinalAsString());
                }
                // Check type
                if (!specField.getType().equals(currField.getType())) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_FIELD, 
                        currField.getName(), currField.getType(), specField.getType());
                }

                requiredFields.remove(currField.getName());
            }
            // If not in spec, it must be private, unless in exception case
            else {
                String parentClass = ast.getParent().getParent().findFirstToken(TokenTypes.IDENT).getText();
                if (isPrivateFieldException(parentClass)) {
                    if (!currField.getVisibility().equals("public")) {
                        log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_FIELD,
                            currField.getName(), currField.getVisibility(), "public");
                    }
                } else {
                    if (!currField.getVisibility().equals("private")) {
                        log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_FIELD,
                            currField.getName(), currField.getVisibility(), "private");
                    }
                }
            }
        }
    }

    private boolean isPrivateFieldException(String parentClassName) {
        boolean result = false;
        for (String exception : PRIVATE_FIELD_EXCEPTIONS) {
            result = exception.equals(parentClassName) || result;
        }
        return result;
    }

    @Override
    public void finishTree(DetailAST rootAST) {
        for (String fieldName : requiredFields.keySet()) {
            RequiredField currField = requiredFields.get(fieldName);
            log(0, MSG_MISSING_FIELD, currField.getName());
        }
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        for (String fieldString : requiredFieldStrings.split(",")) {
            // Reformat escaped characters
            fieldString = fieldString.replaceAll("/comma/", ",");

            RequiredField field = new RequiredField(fieldString);
            requiredFields.put(field.getName(), field);
        }
    }

    public void setField(String fieldString) {
        requiredFieldStrings = fieldString;
    }

    public static class RequiredField {
        private String name;
        private String type;
        private boolean isStatic;
        private boolean isFinal;
        private String visibility;

        public RequiredField(String fieldString) {
            String[] properties = fieldString.split("\\s+");
            this.visibility = properties[0];
            this.isStatic = properties[1].equals("static");
            this.isFinal = properties[2].equals("final");
            this.name = properties[properties.length - 1];

            // Check for generic type: they can mess up whitespace separation in the case of
            // Map<String, Integer>, for example
            int typeStringIndex = 1 + (isStatic ? 1 : 0) + (isFinal ? 1 : 0);
            String typeString = properties[typeStringIndex];
            if (typeString.indexOf("<") != -1) {
                int startIndex = fieldString.indexOf(typeString);
                int endIndex = fieldString.lastIndexOf(">") + 1;
                this.type = fieldString.substring(startIndex, endIndex);
            } else {
                this.type = properties[properties.length - 2];
            }
        }

        public RequiredField(DetailAST ast) {
            if (ast.getType() != TokenTypes.VARIABLE_DEF) {
                throw new IllegalArgumentException("Passed ast must be of type VARIABLE_DEF");
            }

            name = ast.findFirstToken(TokenTypes.IDENT).getText();
            type = getASTAsString(ast.findFirstToken(TokenTypes.TYPE));

            String[] modifiers = new String[]{"", "", ""};
            DetailAST currMod = ast.getFirstChild().getFirstChild();
            int i = 0;
            while (currMod != null) {
                modifiers[i] = currMod.getText();
                currMod = currMod.getNextSibling();
                i++;
            }

            visibility = modifiers[0];
            isStatic = modifiers[1].equals("static");
            isFinal = modifiers[2].equals("final");
        }

        private String getASTAsString(DetailAST ast) {
            // Base case: if there are no more children
            if (ast.getChildCount() == 0) {
                return ast.getText();
            }

            // Recursive case
            String result = "";
            DetailAST currElem = ast.getFirstChild();
            while (currElem != null) {
                result += getASTAsString(currElem);
                currElem = currElem.getNextSibling();
            }

            return result;
        }

        @Override
        public String toString() {
            String staticString = isStatic ? "static " : "";
            String finalString = isFinal ? "final " : "";
            return visibility + " " + staticString + finalString + type + " " + name;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getVisibility() {
            return visibility;
        }

        public boolean getIsStatic() {
            return isStatic;
        }

        public String getStaticAsString() {
            return isStatic ? "static" : "non-static";
        }

        public boolean getIsFinal() {
            return isFinal;
        }

        public String getFinalAsString() {
            return isFinal ? "final" : "non-final";
        }
    }
}