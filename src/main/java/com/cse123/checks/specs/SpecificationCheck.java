package com.cse123.checks.specs;

import java.util.*;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;

@StatelessCheck
public class SpecificationCheck extends AbstractCheck {
    private List<String> requiredFieldNames;
    private List<String> requiredFieldTypes;
    private List<String> requiredMethodNames;
    private List<String> requiredMethodReturnTypes;
    private List<String> requiredMethodParamTypes;
    private List<String> requiredMethodParamNames;

    private Set<RequiredMethod> requiredMethods;
    private Set<RequiredField> requiredFields;

    public static final String MSG_MISSING_METHODS = "missing.methods";

    public static final String MSG_MISSING_FIELDS = "missing.fields";

    public SpecificationCheck() {
        requiredFieldNames = new ArrayList<>();
        requiredFieldTypes = new ArrayList<>();
        requiredMethodNames = new ArrayList<>();
        requiredMethodReturnTypes = new ArrayList<>();
        requiredMethodParamTypes = new ArrayList<>();
        requiredMethodParamNames = new ArrayList<>();

        requiredMethods = new HashSet<>();
        requiredFields = new HashSet<>();
    }

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] { 
            TokenTypes.METHOD_DEF,
            TokenTypes.VARIABLE_DEF
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return CommonUtil.EMPTY_INT_ARRAY;
    }

    @Override
    public void visitToken(DetailAST ast) {

        switch (ast.getType()) {
            case (TokenTypes.METHOD_DEF):
                RequiredMethod thisMethod = new RequiredMethod(ast);

                // Validate all properties
                if (requiredMethods.contains(thisMethod)) {
                    requiredMethods.remove(thisMethod);
                }
                break;
            case (TokenTypes.VARIABLE_DEF):
                // Check if variable is class field
                if (ast.getParent().getParent().getType() == TokenTypes.CLASS_DEF) {
                    RequiredField thisField = new RequiredField(ast);

                    if (requiredFields.contains(thisField)) {
                        requiredFields.remove(thisField);
                    }
                }
                break;
        }
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        // Instantiate required methods
        // TODO: Ensure all are the same length
        for (int i = 0; i < requiredMethodNames.size(); i++) {
            requiredMethods.add(
                new RequiredMethod(
                    requiredMethodNames.get(i),
                    requiredMethodReturnTypes.get(i),
                    requiredMethodParamNames.get(i),
                    requiredMethodParamTypes.get(i)
                )
            );
        }

        // Instantiate required fields
        // TODO: Ensure both field names and types are same length
        for (int i = 0; i < requiredFieldNames.size(); i++) {
            requiredFields.add(
                new RequiredField(
                    requiredFieldNames.get(i), 
                    requiredFieldTypes.get(i)
                )
            );
        }
    }

    @Override
    public void finishTree(DetailAST rootAST) {
        if (requiredMethods.size() > 0) {
            for (RequiredMethod method : requiredMethods) {
                log(0, MSG_MISSING_METHODS);
            }
        }

        if (requiredFields.size() > 0) {
            for (RequiredField field : requiredFields) {
                log(0, MSG_MISSING_FIELDS);
            }
        }
    }

    /**
     * Add a required field name
     * 
     * @param fieldName required field name
     */
    public void setRequiredFieldName(String fieldName) {
        requiredFieldNames.add(fieldName);
    }

    /**
     * Add a required field type
     * 
     * @param fieldType required field type
     */
    public void setRequiredFieldType(String fieldType) {
        requiredFieldTypes.add(fieldType);
    }

    /**
     * Add a required method name
     * 
     * @param methodName required method name
     */
    public void setRequiredMethodName(String methodName) {
        requiredMethodNames.add(methodName);
    }

    /**
     * Add a required method return type
     * 
     */
    public void setRequiredMethodReturnType(String returnType) {
        requiredMethodReturnTypes.add(returnType);
    }

    /**
     * Add a required set of method param types
     * 
     * @param paramTypes whitespace delimited list of method param types
     */
    public void setRequiredMethodParamTypes(String paramTypes) {
        requiredMethodParamTypes.add(paramTypes);
    }
    
    /**
     * Add a required set of method param names
     * 
     * @param paramNames whitespace delimited list of method param names
     */
    public void setRequiredMethodParamNames(String paramNames) {
        requiredMethodParamNames.add(paramNames);
    }

    public static class RequiredMethod {
        private String name;
        private String returnType;
        private Set<RequiredParam> params;

        public RequiredMethod(String name, String returnType, String paramNames, String paramTypes) {
            this.name = name;
            this.returnType = returnType;
            this.params = new HashSet<>();

            String[] newParamNames = paramNames.split(" ");
            String[] newParamTypes = paramTypes.split(" ");
            if (newParamNames.length != newParamTypes.length) {
                throw new IllegalArgumentException("paramNames must match length of paramTypes");
            }
            for (int i = 0; i < newParamNames.length; i++) {
                this.params.add(new RequiredParam(
                    newParamNames[i],
                    newParamTypes[i]
                ));
            }
        }

        public RequiredMethod(DetailAST ast) {
            if (ast.getType() != TokenTypes.METHOD_DEF) {
                throw new IllegalArgumentException("Passed ast must be of type METHOD_DEF");
            }

            name = ast.findFirstToken(TokenTypes.IDENT).getText();
            returnType = ast.findFirstToken(TokenTypes.TYPE).getFirstChild().getText();
            DetailAST paramWrapper = ast.findFirstToken(TokenTypes.PARAMETERS);
            params = new HashSet<>();

            if (paramWrapper != null) {
                DetailAST currentToken = paramWrapper.findFirstToken(TokenTypes.PARAMETER_DEF);

                while (currentToken != null) {
                    if (currentToken.getType() == TokenTypes.PARAMETER_DEF) {
                        params.add(new RequiredParam(currentToken));
                    }
                    currentToken = currentToken.getNextSibling();
                }
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ returnType.hashCode() ^ params.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            boolean same = false;

            if (obj instanceof RequiredMethod) {
                RequiredMethod otherMethod = (RequiredMethod) obj;
                same = name.equals(otherMethod.getName()) ||
                       returnType.equals(otherMethod.getReturnType()) ||
                       params.equals(otherMethod.getParams());
            }

            return same;
        }

        public String getName() {
            return name;
        }

        public String getReturnType() {
            return returnType;
        }

        public Set<RequiredParam> getParams() {
            return params;
        }
    }

    public static class RequiredParam {
        private String name;
        private String type;

        public RequiredParam(String paramName, String paramType) {
            name = paramName;
            type = paramType;
        }

        public RequiredParam(DetailAST ast) {
            this(
                ast.findFirstToken(TokenTypes.IDENT).getText(),
                ast.findFirstToken(TokenTypes.TYPE).getFirstChild().getText()
            );
        }

        @Override
        public int hashCode(){
            return name.hashCode() ^ type.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            boolean same = false;

            if (obj instanceof RequiredParam) {
                RequiredParam otherParam = (RequiredParam) obj;
                same = name.equals(otherParam.getName()) && type.equals(otherParam.getType());
            }

            return same;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    public static class RequiredField {
        private String name;
        private String type;

        public RequiredField(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public RequiredField(DetailAST ast) {
            if (ast.getType() != TokenTypes.VARIABLE_DEF) {
                throw new IllegalArgumentException("Passed ast must be of type VARIABLE_DEF");
            }
            name = ast.findFirstToken(TokenTypes.IDENT).getText();
            type = ast.findFirstToken(TokenTypes.TYPE).getFirstChild().getText();
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ type.hashCode();   
        }

        @Override
        public boolean equals(Object obj) {
            boolean same = false;

            if (obj instanceof RequiredField) {
                RequiredField otherField = (RequiredField) obj;

                same = name.equals(otherField.getName()) &&
                       type.equals(otherField.getType());
            }

            return same;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }
}
