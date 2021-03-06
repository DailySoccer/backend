/**
 * This class is generated by jOOQ
 */
package model.sql;

/**
 * A class modelling foreign key relationships between tables of the <code>public</code> 
 * schema
 */
@javax.annotation.Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.5.0"
	},
	comments = "This class is generated by jOOQ"
)
@java.lang.SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Keys {

	// -------------------------------------------------------------------------
	// IDENTITY definitions
	// -------------------------------------------------------------------------

	public static final org.jooq.Identity<model.sql.tables.records.OptaxmlRecord, java.lang.Integer> IDENTITY_OPTAXML = Identities0.IDENTITY_OPTAXML;

	// -------------------------------------------------------------------------
	// UNIQUE and PRIMARY KEY definitions
	// -------------------------------------------------------------------------

	public static final org.jooq.UniqueKey<model.sql.tables.records.OptaxmlRecord> OPTAXML_PKEY = UniqueKeys0.OPTAXML_PKEY;

	// -------------------------------------------------------------------------
	// FOREIGN KEY definitions
	// -------------------------------------------------------------------------


	// -------------------------------------------------------------------------
	// [#1459] distribute members to avoid static initialisers > 64kb
	// -------------------------------------------------------------------------

	private static class Identities0 extends org.jooq.impl.AbstractKeys {
		public static org.jooq.Identity<model.sql.tables.records.OptaxmlRecord, java.lang.Integer> IDENTITY_OPTAXML = createIdentity(model.sql.tables.Optaxml.OPTAXML, model.sql.tables.Optaxml.OPTAXML.ID);
	}

	private static class UniqueKeys0 extends org.jooq.impl.AbstractKeys {
		public static final org.jooq.UniqueKey<model.sql.tables.records.OptaxmlRecord> OPTAXML_PKEY = createUniqueKey(model.sql.tables.Optaxml.OPTAXML, model.sql.tables.Optaxml.OPTAXML.ID);
	}
}
