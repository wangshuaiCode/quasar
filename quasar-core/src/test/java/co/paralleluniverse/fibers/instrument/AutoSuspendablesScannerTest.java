/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.Type;

public class AutoSuspendablesScannerTest {
    private static AutoSuspendablesScanner scanner;
    private static final Set<String> suspependables = new HashSet<>();
    private static final Set<String> suspependableSupers = new HashSet<>();
    
    @BeforeClass
    public static void buildGraph() throws Exception {
        scanner = new AutoSuspendablesScanner(Paths.get("build", "classes", "test"));
//        scanner = new AutoSuspendablesScanner(
//                Paths.get(AutoSuspendablesScannerTest.class.getClassLoader()
//                        .getResource(AutoSuspendablesScannerTest.class.getName().replace('.', '/') + ".class").toURI()));
        scanner.setAuto(true);
        scanner.run();
        scanner.getSuspenablesAndSupers(suspependables, suspependableSupers);
    }

    @Test
    public void suspendableCallTest() {
        final String method = B.class.getName() + ".foo(I)V";
        assertTrue(suspependables.contains(method));
    }

    @Test
    public void superSuspendableCallTest() {
        final String method = A.class.getName() + ".foo" + Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(IA.class));
        assertTrue(suspependables.contains(method));
    }

    @Test
    public void nonSuperSuspendableCallTest() {
        final String method = A.class.getName() + ".foo()";
        assertTrue(!suspependables.contains(method));
    }

    @Test
    public void superNonSuspendableCallTest() {
        final String method = A.class.getName() + ".bar" + Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(IA.class));
        assertTrue(!suspependables.contains(method));
    }

    @Test
    public void superSuspendableTest() {
        final String method = IA.class.getName() + ".foo(I)V";
        assertTrue(suspependableSupers.contains(method));
    }

    @Test
    public void suspendableFileByAntTaskTest() {
        String suspFile = AutoSuspendablesScannerTest.class.getClassLoader().getResource("META-INF/testSuspendables").getFile();
        SimpleSuspendableClassifier ssc = new SimpleSuspendableClassifier(suspFile);
        assertTrue(ssc.isSuspendable(B.class.getName().replace(".", "/"), "foo", "(I)V"));
    }

    static interface IA {
        // super suspendable
        void foo(int t);

        // doesn't have suspandable implementation
        void bar(int t);
    }

    static class A {
        // suspendable
        void foo(IA a) {
            a.foo(0);
        }

        // not suspendable
        void foo() {
            bar(null); // test that if foo->bar->foo->... doesn't cause infinite loop
        }

        // not suspendable
        void bar(IA a) {
            a.bar(0);
            foo();
        }
    }

    static class B implements IA {
        // suspendable
        @Override
        public void foo(int t) {
            try {
                Fiber.park();
            } catch (SuspendExecution ex) {
                throw new RuntimeException(ex);
            }
        }

        // not suspendable
        @Override
        public void bar(int t) {
        }
    }
}
