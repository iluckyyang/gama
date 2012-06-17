/**
 * Created by drogoul, 26 mars 2012
 * 
 */
package msi.gaml.compilation;

import msi.gaml.statements.Facets;

/**
 * The class StringBasedStatementDescription.
 * 
 * @author drogoul
 * @since 26 mars 2012
 * 
 */
public class SyntheticStatement extends AbstractSyntacticStatement {

	/**
	 * @param keyword
	 * @param facets
	 */
	public SyntheticStatement(final String keyword, final Facets facets) {
		super(keyword);
		this.facets.putAll(facets);
	}

	public SyntheticStatement(final String keyword) {
		super(keyword);
	}

	@Override
	public boolean isSynthetic() {
		return true;
	}

}
