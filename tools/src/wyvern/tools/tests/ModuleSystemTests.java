package wyvern.tools.tests;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import wyvern.target.corewyvernIL.expression.IntegerLiteral;
import wyvern.target.corewyvernIL.support.Util;
import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.imports.extensions.WyvernResolver;
import wyvern.tools.parsing.coreparser.ParseException;
import wyvern.tools.tests.suites.RegressionTests;

@Category(RegressionTests.class)
public class ModuleSystemTests {

    private static final String BASE_PATH = TestUtil.BASE_PATH;
    private static final String PATH = BASE_PATH + "modules/";

    @BeforeClass public static void setupResolver() {
        TestUtil.setPaths();
        WyvernResolver.getInstance().addPath(PATH);
    }

    @Test
    public void testInst() throws ParseException {
        String program = TestUtil.readFile(PATH + "inst.wyv");
        TestUtil.getNewAST(program, "test input");
    }

    @Test
    public void testADT() throws ParseException {
        TestUtil.doTestScriptModularly("modules.listClient",
                Util.intType(),
                new IntegerLiteral(5));
    }

    @Test
    public void testTransitiveAuthorityGood() throws ParseException {
        TestUtil.doTestScriptModularly("modules.databaseClientGood",
                Util.intType(),
                new IntegerLiteral(1));
    }

    @Test
    public void testTransitiveAuthorityBad() throws ParseException {
        TestUtil.doTestScriptModularlyFailing("modules.databaseClientBad",
                ErrorMessage.NO_SUCH_METHOD);
    }

    @Test
    public void testTopLevelVars() throws ParseException {
        TestUtil.doTestScriptModularly("modules.databaseUser",
                Util.intType(),
                new IntegerLiteral(10));
    }

    @Test
    public void testTopLevelVarsWithAliasing() throws ParseException {
        TestUtil.doTestScriptModularly("modules.databaseUserTricky",
                Util.intType(),
                new IntegerLiteral(10));
    }

    @Test
    public void testTopLevelVarGet() throws ParseException {
        String source = "var v : Int = 5\n"
                + "v\n";

        TestUtil.doTestInt(source, 5);
    }

    @Test
    public void testTopLevelVarSet() throws ParseException {

        String source = "var v : Int = 5\n"
                + "v = 10\n"
                + "v\n";
        TestUtil.doTestInt(source, 10);
    }

    @Test
    public void testSimpleADT() throws ParseException {
        TestUtil.doTestScriptModularly("modules.simpleADTdriver", Util.intType(), new IntegerLiteral(5));
    }

    @Test
    public void testSimpleADTWithRenamingImport() throws ParseException {
        TestUtil.doTestScriptModularly("modules.simpleADTdriver2", Util.intType(), new IntegerLiteral(5));
    }

    @Test
    public void testSimpleADTWithRenamingRequire() throws ParseException {
        TestUtil.doTestScriptModularly("modules.simpleADTdriver3", Util.intType(), new IntegerLiteral(5));
    }
}
