grammar Jimple;

@header {
package org.clyze.jimple;
}

program
	: klass ;


klass
	: modifier* ('class'|'interface') IDENTIFIER 'extends' IDENTIFIER ('implements' identifierList)? '{' (field|method)*'}' ;

modifier
	: 'public'
	| 'protected'
	| 'private'
	| 'static'
	| 'abstract'
	| 'final'
	| 'transient'
	| 'synchronized'
	;

field
	: modifier* IDENTIFIER '[]'? IDENTIFIER ';' ;

method
	: modifier* IDENTIFIER IDENTIFIER '(' identifierList? ')' ('throws' identifierList)? (methodBody | ';') ;

identifierList
	: IDENTIFIER
	| identifierList ',' IDENTIFIER
	;

methodBody
	: '{' (statement ';' | IDENTIFIER ':')+ '}' ;

statement
	: declarationStmt
	| assignmentStmt
	| returnStmt
	| invokeStmt
	| allocationStmt
	| jumpStmt
	| switchStmt
	| catchStmt
	;

declarationStmt
	: IDENTIFIER identifierList ;

assignmentStmt
	: IDENTIFIER ':=' IDENTIFIER ':' IDENTIFIER
	| IDENTIFIER ':=' '@caughtexception'
	| IDENTIFIER '=' value
	| IDENTIFIER '=' '(' IDENTIFIER ')' value
	| IDENTIFIER '=' ('lengthof'|'class') value
	| IDENTIFIER '=' value (OP|'cmp'|'cmpl'|'instanceof') value
	| IDENTIFIER '=' value '[' value ']'
	| IDENTIFIER '[' value ']' '=' value ('[' value ']')?
	| IDENTIFIER '=' (IDENTIFIER '.')? fieldSig
	| (IDENTIFIER '.')? fieldSig '=' value
	| IDENTIFIER '=' 'newarray' '(' IDENTIFIER ')' '[' value ']'
	;

returnStmt
	: 'return' value? ;

invokeStmt
	: (IDENTIFIER '=')? ('specialinvoke'|'virtualinvoke'|'interfaceinvoke') IDENTIFIER '.' methodSig '(' valueList? ')'
	| (IDENTIFIER '=')? 'staticinvoke' methodSig '(' valueList? ')'
	;

allocationStmt
	: IDENTIFIER '=' 'new' IDENTIFIER ;

methodSig
	: '<' IDENTIFIER ':' IDENTIFIER IDENTIFIER '(' identifierList* ')' '>' ;

fieldSig
	: '<' IDENTIFIER ':' IDENTIFIER IDENTIFIER '>' ;

value
	: IDENTIFIER
	| INTEGER
	| REAL
	| STRING
	;

valueList
	: value
	| valueList ',' value
	;

jumpStmt
	: ('if' value ('==' | '!=' | '<' | '<=' | '>' | '>=') value)? 'goto' IDENTIFIER ;

switchStmt
	: ('tableswitch'|'lookupswitch') '(' value ')' '{' caseStmt* '}' ;

caseStmt
	: ('case' INTEGER|'default') ':' 'goto' IDENTIFIER ';' ;

catchStmt
	: 'catch' IDENTIFIER 'from' IDENTIFIER 'to' IDENTIFIER 'with' IDENTIFIER ;


// Lexer

INTEGER
	: '-'?[0-9]+'L'?
	| '-'?'0'[0-7]+'L'?
	| '-'?'0'[xX][0-9a-fA-F]+'L'?
	;

fragment
EXPONENT
	: [eE][-+]?INTEGER ;

REAL
	: INTEGER EXPONENT
	| INTEGER EXPONENT? [fF]
	| (INTEGER)? '.' INTEGER EXPONENT? [fF]?
	;

BOOLEAN
	: 'true' | 'false' ;

STRING
    : '"' STRING_CHAR* '"' ;

fragment
STRING_CHAR
    : ~["\\]
    | '\\' [ubtnfr"'\\]
    ;

fragment
IDENTIFIER_BASE
	: [$@a-zA-Z_][$@a-zA-Z0-9_]* ;

fragment
IDENTIFIER_SUF
	: '#_' [0-9]+ ;

IDENTIFIER
	: IDENTIFIER_BASE ('.' IDENTIFIER_BASE)* IDENTIFIER_SUF? '[]'*
	| '<' IDENTIFIER_BASE '>'
	| '\'' IDENTIFIER_BASE '\''
	;

OP
	: '+' | '-' | '*' | '/' | '%' | '&' | '|' | '^' | '<<' | '>>' | '>>>' ;


WHITE_SPACE
	: [ \t\r\n]+ -> skip ;
