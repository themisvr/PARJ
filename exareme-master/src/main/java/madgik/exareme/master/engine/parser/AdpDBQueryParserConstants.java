/* Generated By:JavaCC: Do not edit this line. AdpDBQueryParserConstants.java */
package madgik.exareme.master.engine.parser;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface AdpDBQueryParserConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int SINGLE_LINE_COMMENT = 2;
  /** RegularExpression Id. */
  int SINGLE_LINE_COMMENT_2 = 3;
  /** RegularExpression Id. */
  int MULTI_LINE_COMMENT = 4;
  /** RegularExpression Id. */
  int USING = 6;
  /** RegularExpression Id. */
  int DISTRIBUTED = 7;
  /** RegularExpression Id. */
  int CREATE = 8;
  /** RegularExpression Id. */
  int DROP = 9;
  /** RegularExpression Id. */
  int TEMP = 10;
  /** RegularExpression Id. */
  int TEMPORARY = 11;
  /** RegularExpression Id. */
  int TABLE = 12;
  /** RegularExpression Id. */
  int INDEX = 13;
  /** RegularExpression Id. */
  int TO = 14;
  /** RegularExpression Id. */
  int BROADCAST = 15;
  /** RegularExpression Id. */
  int PARTITIONED = 16;
  /** RegularExpression Id. */
  int ON = 17;
  /** RegularExpression Id. */
  int AS = 18;
  /** RegularExpression Id. */
  int DIRECT = 19;
  /** RegularExpression Id. */
  int DIRECTSCRIPT = 20;
  /** RegularExpression Id. */
  int TREE = 21;
  /** RegularExpression Id. */
  int EXTERNAL = 22;
  /** RegularExpression Id. */
  int REMOTE = 23;
  /** RegularExpression Id. */
  int VIRTUAL = 24;
  /** RegularExpression Id. */
  int SELECT = 25;
  /** RegularExpression Id. */
  int COMMA = 26;
  /** RegularExpression Id. */
  int AT = 27;
  /** RegularExpression Id. */
  int SEMICOLON = 28;
  /** RegularExpression Id. */
  int QUOTEDSTRING = 29;
  /** RegularExpression Id. */
  int DQUOTEDSTRING = 30;
  /** RegularExpression Id. */
  int BLOCK = 31;
  /** RegularExpression Id. */
  int SBBLOCK = 32;
  /** RegularExpression Id. */
  int SQL_QUERY = 33;
  /** RegularExpression Id. */
  int WHITE = 34;
  /** RegularExpression Id. */
  int DIGIT = 35;
  /** RegularExpression Id. */
  int NUMBER = 36;
  /** RegularExpression Id. */
  int IDENTIFIER = 37;
  /** RegularExpression Id. */
  int LETTER = 38;
  /** RegularExpression Id. */
  int PART_LETTER = 39;

  /** Lexical state. */
  int DEFAULT = 0;
  /** Lexical state. */
  int IN_MULTI_LINE_COMMENT = 1;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "\"/*\"",
    "<SINGLE_LINE_COMMENT>",
    "<SINGLE_LINE_COMMENT_2>",
    "\"*/\"",
    "<token of kind 5>",
    "\"using\"",
    "\"distributed\"",
    "\"create\"",
    "\"drop\"",
    "\"temp\"",
    "\"temporary\"",
    "\"table\"",
    "\"index\"",
    "\"to\"",
    "\"broadcast\"",
    "\"partitioned\"",
    "\"on\"",
    "\"as\"",
    "\"direct\"",
    "\"directscript\"",
    "\"tree\"",
    "\"external\"",
    "\"remote\"",
    "\"virtual\"",
    "\"select\"",
    "\",\"",
    "\"at\"",
    "\";\"",
    "<QUOTEDSTRING>",
    "<DQUOTEDSTRING>",
    "<BLOCK>",
    "<SBBLOCK>",
    "<SQL_QUERY>",
    "<WHITE>",
    "<DIGIT>",
    "<NUMBER>",
    "<IDENTIFIER>",
    "<LETTER>",
    "<PART_LETTER>",
    "\"(\"",
    "\")\"",
    "\".\"",
  };

}
