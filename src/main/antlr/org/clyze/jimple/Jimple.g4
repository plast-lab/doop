grammar Jimple;

@header {
package org.clyze.jimple;
}

program
	: klass ;

klass
	: modifier* ('class'|'interface') IDENTIFIER ('extends' IDENTIFIER)? ('implements' identifierList)? '{' (field|method)* '}' ;

modifier
	: 'public'
	| 'protected'
	| 'private'
	| 'static'
	| 'abstract'
	| 'final'
	| 'transient'
	| 'synchronized'
	| 'volatile'
	| 'native'
	| 'enum'
	| 'strictfp'
	| IDENTIFIER // IDENTIFIER added to support the 'annotation' keyword which can be used as a method name etc as well
	;

field
	: modifier* IDENTIFIER '[]'? IDENTIFIER ';' ;

method
	: modifier* IDENTIFIER IDENTIFIER '(' identifierList? ')' throwsExceptions? (methodBody | ';') ;

throwsExceptions
	: 'throws' identifierList ;

identifierList
	: IDENTIFIER MARKER?
	| identifierList ',' IDENTIFIER MARKER?
	;

methodBody
	: '{' ( ('(' INTEGER ')')? statement ';' | IDENTIFIER ':')+ '}' ;

statement
	: declarationStmt
	| complexAssignmentStmt
	| assignmentStmt
	| returnStmt
	| invokeStmt
	| allocationStmt
	| jumpStmt
	| switchStmt
	| catchStmt
	| monitorStmt
	| nopStmt
	;

declarationStmt
	: IDENTIFIER identifierList ;

complexAssignmentStmt
	: IDENTIFIER '[' value ']' '=' value ('[' value ']')?
	| (IDENTIFIER '.')? fieldSig '=' value
	;

assignmentStmt
	: IDENTIFIER ':=' IDENTIFIER ':' IDENTIFIER
	| IDENTIFIER ':=' '@caughtexception'
	| IDENTIFIER '=' value
	| IDENTIFIER '=' '(' IDENTIFIER ')' value
	| IDENTIFIER '=' ('lengthof'|'class'|'neg') value
	| IDENTIFIER '=' value (OP|'cmp'|'cmpl'|'cmpg'|'instanceof') value
	| IDENTIFIER '=' value '[' value ']'
	| IDENTIFIER '=' (IDENTIFIER '.')? fieldSig
	| IDENTIFIER '=' 'Phi' '(' identifierList ')'
	;

returnStmt
	: 'return' value? ;

invokeStmt
	: (IDENTIFIER '=')? ('specialinvoke'|'virtualinvoke'|'interfaceinvoke') IDENTIFIER '.' methodSig '(' valueList? ')'
	| (IDENTIFIER '=')? 'staticinvoke' methodSig '(' valueList? ')'
	| (IDENTIFIER '=')? 'dynamicinvoke' STRING dynamicMethodSig '(' valueList? ')' methodSig '(' bootValueList? ')'
	;

allocationStmt
	: IDENTIFIER '=' 'new' IDENTIFIER
	| IDENTIFIER '=' 'newarray' '(' IDENTIFIER ')' '[' value ']'
	| IDENTIFIER '=' 'newmultiarray' '(' IDENTIFIER ')' ('[' value? ']')+ '[]'?
	;

methodSig
	: '<' IDENTIFIER ':' IDENTIFIER IDENTIFIER '(' identifierList? ')' '>' ;

dynamicMethodSig
	: '<' IDENTIFIER '(' identifierList? ')' '>' ;

fieldSig
	: '<' IDENTIFIER ':' IDENTIFIER IDENTIFIER '>' ;

value
	: IDENTIFIER
	| INTEGER
	| REAL
	| STRING
	| 'class' STRING
	| 'handle:' methodSig
	;

valueList
	: value
	| valueList ',' value
	;

bootValueList
	: valueList;

jumpStmt
	: ('if' value ('==' | '!=' | '<' | '<=' | '>' | '>=') value)? 'goto' IDENTIFIER ;

switchStmt
	: ('tableswitch'|'lookupswitch') '(' value ')' '{' caseStmt* '}' ;

caseStmt
	: ('case' INTEGER|'default') ':' 'goto' IDENTIFIER ';' ;

catchStmt
	: 'catch' IDENTIFIER 'from' IDENTIFIER 'to' IDENTIFIER 'with' IDENTIFIER ;

monitorStmt
	: 'entermonitor' 'class' STRING
	| 'exitmonitor' 'class' STRING
	| 'entermonitor' IDENTIFIER
	| 'exitmonitor' IDENTIFIER
	;

nopStmt
	: 'nop' ;


// Lexer

INTEGER
	: '-'?[0-9]+'L'?
	| '-'?'0'[0-7]+'L'?
	| '-'?'0'[xX][0-9a-fA-F]+'L'?
	;

MARKER
	: '#'INTEGER ;

fragment
EXPONENT
	: [eE][-+]?INTEGER ;

REAL
	: INTEGER EXPONENT
	| INTEGER EXPONENT? [fF]
	| (INTEGER)? '.' INTEGER EXPONENT? [fF]?
	| '#Infinity'
	| '#-Infinity'
	| '#InfinityF'
	| '#-InfinityF'
	| '#NaN'
	| '#NaNF'
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
	: [$@a-zA-Z0-9_][$@a-zA-Z0-9_-]*
	;

fragment
IDENTIFIER_SUF
	: '#_' [0-9]+ ;

fragment
PACKAGE_PART
	: '.' IDENTIFIER_BASE
	| '.' '\'' IDENTIFIER_BASE '\''
	;

IDENTIFIER
	: IDENTIFIER_BASE PACKAGE_PART* IDENTIFIER_SUF? '[]'*
	| '<' IDENTIFIER_BASE '>'
	| '\'' IDENTIFIER_BASE '\''
	;

OP
	: '+' | '-' | '*' | '/' | '%' | '&' | '|' | '^' | '<<' | '>>' | '>>>' ;


WHITE_SPACE
	: [ \t\r\n]+ -> skip ;
