package com.puppycrawl.tools.checkstyle.checks.imports;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

@StatelessCheck
public class StarImportCheck
    extends AbstractCheck {

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_KEY = "import.starImport";

    /** Suffix for the star import. */
    private static final String STAR_IMPORT_SUFFIX = ".*";

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
        // original implementation checks both IMPORT and STATIC_IMPORT tokens to avoid ".*" imports
        // however user can allow using "import" or "import static"
        // by configuring allowClassImports and allowStaticMemberImports
        // To avoid potential confusion when user specifies conflicting options on configuration
        // (see example below) we are adding both tokens to Required list
        //   <module name="AvoidStarImport">
        //      <property name="tokens" value="IMPORT"/>
        //      <property name="allowStaticMemberImports" value="false"/>
        //   </module>
        return new int[] {TokenTypes.IMPORT};
    }

    @Override
    public void visitToken(final DetailAST ast) {
        if (ast.getType() == TokenTypes.IMPORT) {
            final DetailAST startingDot = ast.getFirstChild();
            logsStarredImportViolation(startingDot);
        }
    }

    /**
     * Gets the full import identifier.  If the import is not a starred import and
     * it is included then a violation is logged.
     *
     * @param startingDot the starting dot for the import statement
     */
    private void logsStarredImportViolation(DetailAST startingDot) {
        final FullIdent name = FullIdent.createFullIdent(startingDot);
        final String importText = name.getText();
        if (!importText.endsWith(STAR_IMPORT_SUFFIX)) {
            log(startingDot, MSG_KEY, importText);
        }
    }

}
