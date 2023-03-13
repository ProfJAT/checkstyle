package com.cse123.checks.naming;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.checks.naming.AbstractAccessControlNameCheck;
import com.puppycrawl.tools.checkstyle.utils.ScopeUtil;

public class ClassConstantNameCheck
    extends AbstractAccessControlNameCheck {

    /** Creates a new {@code ClassConstantNameCheck} instance. */
    public ClassConstantNameCheck() {
        super("\b[A-Z]+(_[A-Z]+)*\b");
    }

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    @Override
    protected final boolean mustCheckName(DetailAST ast) {
        final DetailAST modifiersAST =
            ast.findFirstToken(TokenTypes.MODIFIERS);
        final boolean isStatic = modifiersAST.findFirstToken(TokenTypes.LITERAL_STATIC) != null;
        final boolean isFinal = modifiersAST.findFirstToken(TokenTypes.FINAL) != null;

        return isStatic
                && isFinal
                && shouldCheckInScope(modifiersAST)
                && !ScopeUtil.isInInterfaceOrAnnotationBlock(ast);
    }

}
