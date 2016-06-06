/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev, Synopsys Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.synopsys.arc.jenkins.plugins.rolestrategy;

import org.junit.Test;
import org.junit.Assert;

/**
 * Contains tests for Macro.
 * @see Macro
 * @since 2.1.0
 * @author Oleg Nenashev
 */
public class MacroTest {
    
    @Test 
    public void CorrectFormatsSanityCheck() {
        parseMacro("@Dummy");
        parseMacro("@Dummy:1");
        parseMacro("@Dummy(aaa)");
        parseMacro("@Dummy:1(aaa)");
        parseMacro("@Dummy(aaa,bbb,ccc,ddd)");
        parseMacro("@Dummy:1(aaa,bbb,ccc,ddd)");  
    }
    
    @Test 
    public void TestValidUsers() {
        parseWrongMacro("test", MacroExceptionCode.Not_Macro);
        parseWrongMacro("logged_user", MacroExceptionCode.Not_Macro);
        parseWrongMacro("anonymous", MacroExceptionCode.Not_Macro);
        parseWrongMacro("_anonymous", MacroExceptionCode.Not_Macro);
        parseWrongMacro("dummy user with spaces", MacroExceptionCode.Not_Macro);      
    }
    
    @Test
    public void TestWrongBrackets() {
        parseWrongMacro("@test:1(aaa,bbb(", MacroExceptionCode.WrongFormat);
        parseWrongMacro("@test:1(aaa,bbb(", MacroExceptionCode.WrongFormat); 
        parseWrongMacro("@test:1()(aaa,bbb)", MacroExceptionCode.WrongFormat); 
        parseWrongMacro("@test:1[aaa,bbb]", MacroExceptionCode.WrongFormat); 
        parseWrongMacro("@test:1)aaa,bbb(", MacroExceptionCode.WrongFormat); 
        parseWrongMacro("@test:1)(", MacroExceptionCode.WrongFormat); 
    }
    
    @Test
    public void ERR_WrongEnding() {
        parseWrongMacro("@test:1(aaa,bbb)error", MacroExceptionCode.WrongFormat);
    }
    
    @Test
    public void ERR_Quotes() {
        parseWrongMacro("@test':1(aaa,bbb)", MacroExceptionCode.WrongFormat);
        parseWrongMacro("@'test':1(aaa,bbb)", MacroExceptionCode.WrongFormat);
        parseWrongMacro("@test:1('aaa',bbb)", MacroExceptionCode.WrongFormat);
        parseWrongMacro("@test:1'(aaa,bbb)'", MacroExceptionCode.WrongFormat);
        parseWrongMacro("@test:'1'(aaa,bbb)", MacroExceptionCode.WrongFormat);
        parseWrongMacro("'@test:1(aaa,bbb)'", MacroExceptionCode.Not_Macro);
        parseWrongMacro("@test\":1(aaa,bbb)", MacroExceptionCode.WrongFormat);       
        parseWrongMacro("@test:1(aaa,\"bbb\")", MacroExceptionCode.WrongFormat);
        parseWrongMacro("\"@test:1(aaa,bbb)\"", MacroExceptionCode.Not_Macro);
        parseWrongMacro("\"@\"test:1(aaa,bbb)", MacroExceptionCode.Not_Macro);
    }
    
    @Test 
    public void EmptyParameters() {
         parseMacro("@Dummy()");
         parseMacro("@Dummy:1()");      
    }
    
    @Test
    public void test_MacroSanity() {
        testCycle("test1", null, null);
        testCycle("test2", 1, null);
        testCycle("test3", -1, null);
        testCycle("test4", null, new String[]{});
        testCycle("test5", null, new String[]{"aaa"});
        testCycle("test6", -1, new String[]{"aaa"});
        testCycle("test7", null, new String[]{"aaa", "bbb"});
        testCycle("test8", null, new String[]{"aaa", "bbb", "ccc","ddd"});
        testCycle("test8", 123, new String[]{"aaa", "bbb", "ccc","ddd"});        
    }
    
    public static void testCycle(String macroName, Integer macroId, String[] parameters)
    {
        // Create, get string and parse
        Macro macro = new Macro(macroName, macroId, parameters);
        String macroString = macro.toString();
        Macro resMacro = parseMacro("Can't parse generated macro: "+macroString, macroString);
        
        // Compare 
        assertEquals(macro, resMacro);
    }
    
    public static void assertEquals(Macro expected, Macro actual) {
        Assert.assertEquals("Wrong name", expected.getName(), expected.getName());
        Assert.assertEquals("Wrong index", expected.getIndex(), expected.getIndex());
        
        if (expected.hasParameters()) {
            Assert.assertArrayEquals("Wrong parameters set", expected.getParameters(), actual.getParameters());
        }
        else {
            Assert.assertFalse("Actual macro shouldn't have parameters", actual.hasParameters());
        }
    }
    
    private static Macro assertParseMacroDriver(String errorMessage, 
            String macroString, boolean expectException, 
            MacroExceptionCode expectedError) 
    {
        Macro res = null;
        try 
        {
            res = Macro.parse(macroString);
        }
        catch (MacroException ex) 
        {
            ex.printStackTrace();
            if (expectException) {
                Assert.assertEquals(errorMessage + ". Wrong error code" , 
                        expectedError, ex.getErrorCode());
            } 
            else {
                Assert.fail(errorMessage + ". Got Macro Exception: "+ex.getMessage());
            }
            return res;
        }
        
        if (expectException) {
            Assert.fail(errorMessage + ". Haven't got exception. Expected code is "+expectedError);
        }
        return res;
    }
    
    private static Macro parseMacro(String errorMessage, String macroString)
    {
        return assertParseMacroDriver(errorMessage, macroString, false, MacroExceptionCode.UnknownError);
    }
    
    private static Macro parseMacro(String macroString)
    {
        return parseMacro("Parse error", macroString);
    }
    
    private static void parseWrongMacro(String errorMessage, String macroString, MacroExceptionCode expectedCode)
    {
        assertParseMacroDriver(errorMessage, macroString, true, expectedCode);
    }
    
    private static void parseWrongMacro(String macroString, MacroExceptionCode expectedCode)
    {
        parseWrongMacro("Wrong macro parse error", macroString, expectedCode);
    }
    
}
