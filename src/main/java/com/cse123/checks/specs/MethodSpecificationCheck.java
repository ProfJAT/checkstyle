package com.cse123.checks.specs;

import java.util.*;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;

@StatelessCheck
public class MethodSpecificationCheck extends AbstractCheck {
    private Map<String, List<RequiredMethod>> requiredMethods;
    private String requiredMethodStrings;

    public static final String MSG_MALFORMED_METHOD = "malformed.method";

    public static final String MSG_MISSING_METHOD = "missing.method";

    public MethodSpecificationCheck() {
        requiredMethods = new HashMap<>();
        requiredMethodStrings = "";
    }

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.METHOD_DEF,
            TokenTypes.CTOR_DEF
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return CommonUtil.EMPTY_INT_ARRAY;
    }

    @Override
    public void visitToken(DetailAST ast) {
        RequiredMethod currMethod = new RequiredMethod(ast);

        // IF name found in specification, set that it was found
        if (requiredMethods.keySet().contains(currMethod.getName())) {
            // Any matches by name?
            List<RequiredMethod> nameMatches = requiredMethods.get(currMethod.getName());

            if (nameMatches.contains(currMethod)) {
                if (nameMatches.size() == 1) {
                    requiredMethods.remove(currMethod.getName());
                } else {
                    requiredMethods.get(currMethod.getName()).remove(currMethod);
                }
            } else {
                // Log discrepencies
                RequiredMethod specMethod = nameMatches.get(0);
                
                // Check visibility
                if (!specMethod.getVisibility().equals(currMethod.getVisibility())) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_METHOD, 
                        currMethod.getName(), currMethod.getVisibility(), specMethod.getVisibility());
                }
                // Check static
                if (!specMethod.getIsStatic() == currMethod.getIsStatic()) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_METHOD, 
                        currMethod.getName(), currMethod.getStaticAsString(), specMethod.getStaticAsString());
                }
                // Check return type
                if (!specMethod.getReturnType().equals(currMethod.getReturnType())) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_METHOD, 
                        currMethod.getName(), currMethod.getReturnType(), specMethod.getReturnType());
                }
                // Check params
                if (!specMethod.getParams().equals(currMethod.getParams())) {
                    log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_METHOD,
                        currMethod.getName(), currMethod.getParamsAsString(), specMethod.getParamsAsString());
                }

                if (nameMatches.size() == 1) {
                    requiredMethods.remove(specMethod.getName());
                } else {
                    requiredMethods.get(specMethod.getName()).remove(0);
                }
            }

        } else {
            // Check non-spec method is private
            if (!currMethod.getVisibility().equals("private")) {
                log(ast.getLineNo(), ast.getColumnNo(), MSG_MALFORMED_METHOD,
                    currMethod.getName(), currMethod.getVisibility(), "private");
            }
        }
    }

    @Override
    public void finishTree(DetailAST rootAST) {
        for (String methodName : requiredMethods.keySet()) {
            for (RequiredMethod currMethod : requiredMethods.get(methodName)) {
                log(0, MSG_MISSING_METHOD, currMethod.getName());
            }
        }
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        if (requiredMethodStrings.length() > 0) {
            for (String methodString : requiredMethodStrings.split(",")) {
                RequiredMethod method = new RequiredMethod(methodString);

                if (requiredMethods.keySet().contains(method.getName())) {
                    requiredMethods.get(method.getName()).add(method);
                } else {
                    requiredMethods.put(method.getName(), new ArrayList<>(){{
                        add(method);
                    }});
                }
            }
        }
    }

    public void setMethod(String methodString) {
        requiredMethodStrings = methodString;
    }
    
    public static class RequiredMethod {
        private String visibility;
        private boolean isStatic;
        private String returnType;
        private String name;
        private Set<RequiredParam> params;

        public RequiredMethod(String methodString) {
            String[] methodStringSplit = methodString.trim().split("\\(");
            String propertiesString = methodStringSplit[0];
            String paramString = methodStringSplit[1].substring(0, methodStringSplit[1].length() - 1);

            // Handle modifiers/properties
            String[] properties = propertiesString.split("\\s+");

            this.visibility = properties[0];
            this.isStatic = properties[1].equals("static");

            // Handle constructors
            if (properties.length == 2) {
                this.returnType = "";
            } else {
                // Check for generic type: they can mess up whitespace separation in the case of
                // Map<String, Integer>, for example
                String returnTypeString = isStatic ? properties[2] : properties[1];
                if (returnTypeString.indexOf("<") != -1) { // Is generic type
                    int startIndex = methodString.indexOf(returnTypeString);
                    int endIndex = methodString.lastIndexOf(">");
                    this.returnType = methodString.substring(startIndex, endIndex + 1).replace("/comma/", ",");
                } else {
                    this.returnType = properties[properties.length - 2];
                }
            }

                this.name = properties[properties.length - 1];
            
            // Handle parameters
            params = new HashSet<>();
            for (String param : paramString.split(",")) {
                param = param.replace("/comma/", ",");
                params.add(new RequiredParam(param));
            }
        }

        public RequiredMethod(DetailAST ast) {
            if (ast.getType() != TokenTypes.METHOD_DEF && ast.getType() != TokenTypes.CTOR_DEF) {
                throw new IllegalArgumentException("Passed ast must be of type METHOD_DEF or CTOR_DEF");
            }

            name = ast.findFirstToken(TokenTypes.IDENT).getText();
            if (ast.getType() == TokenTypes.CTOR_DEF) {
                returnType = "";
            } else {
                returnType = getASTAsString(ast.findFirstToken(TokenTypes.TYPE));
            }

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
        public int hashCode() {
            return name.hashCode() ^ returnType.hashCode() ^ visibility.hashCode() ^ params.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            boolean same = false;

            if (obj instanceof RequiredMethod) {
                RequiredMethod otherMethod = (RequiredMethod) obj;
                same = name.equals(otherMethod.getName()) &&
                       isStatic == otherMethod.getIsStatic() &&
                       returnType.equals(otherMethod.getReturnType()) &&
                       visibility.equals(otherMethod.getVisibility()) &&
                       params.equals(otherMethod.getParams());
            }

            return same;
        }

        @Override
        public String toString() {
            String staticString = isStatic ? "static " : "";

            String paramString = "";
            for (RequiredParam param : params) {
                paramString += param.toString() + ", ";
            }
            paramString = paramString.substring(0, paramString.length() - 2);

            return visibility + " " + staticString + returnType + " " + name + "(" + paramString + ")";
        }

        public String getName() {
            return name;
        }

        public String getReturnType() {
            return returnType;
        }

        public String getVisibility() {
            return visibility;
        }

        public Set<RequiredParam> getParams() {
            return params;
        }

        public String getParamsAsString() {
            String paramString = "";

            for (RequiredParam param : params) {
                paramString += param.toString() + ", ";
            }

            if (paramString.length() > 0) {
                paramString = paramString.substring(0, paramString.length() - 2);
            }

            return paramString;
        }

        public boolean getIsStatic() {
            return isStatic;
        }

        public String getStaticAsString() {
            return isStatic ? "static" : "non-static";
        }
    }

    public static class RequiredParam {
        private String name;
        private String type;

        public RequiredParam(String paramString) {
            String[] elements = paramString.trim().split(" ");
            name = elements[elements.length - 1];
            type = String.join("", Arrays.copyOfRange(elements, 0, elements.length - 1));
        }

        public RequiredParam(DetailAST ast) {
            name = ast.findFirstToken(TokenTypes.IDENT).getText();
            type = getASTAsString(ast.findFirstToken(TokenTypes.TYPE));
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

        @Override
        public String toString() {
            return type + " " + name;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }
}
