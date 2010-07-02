package edu.vub.at.iat;

import edu.vub.at.IAT;

import junit.framework.TestCase;

public class IATTest extends TestCase {
	
	private boolean isBalanced(String str) {
		return IAT.countBalanced(str) == 0;
	}
	
	public void testIsBalanced() {
		assertTrue(isBalanced("()"));
		assertTrue(isBalanced("[]"));
		assertTrue(isBalanced("{}"));
		assertFalse(isBalanced("{"));
		assertFalse(isBalanced("}"));
		assertFalse(isBalanced("def o := object: {"));
		assertTrue(isBalanced("def o := object: {\n}"));
	}
	
}
