grammar Datalog;

@header {
package org.clyze.deepdoop.datalog;
}

program
	: (comp | cmd | initialize | propagate | datalog)* ;


comp
	: COMP IDENTIFIER (':' IDENTIFIER)? L_BRACK datalog* R_BRACK (AS identifierList)? ;

cmd
	: CMD IDENTIFIER L_BRACK datalog* R_BRACK (AS identifierList)? ;

initialize
	: IDENTIFIER AS identifierList ;

propagate
	: IDENTIFIER '{' propagationList '}' '->' (IDENTIFIER | GLOBAL) ;

identifierList
	: IDENTIFIER
	| identifierList ',' IDENTIFIER
	;

propagationElement
	: ALL
	| predicateName AS predicateName
	| predicateName
	;

propagationList
	: propagationElement
	| propagationList ',' propagationElement
	;


datalog
	: declaration | constraint | rule_ | lineMarker ;


declaration
	: predicate '->' predicateList? '.'
	| singleAtom ',' refmode '->' singleAtom '.'
	;

constraint
	: compound '->' compound '.' ;

rule_
	: predicateList '.'
	| predicateList '<-' compound '.'
	| functional '<-' aggregation '.'
	;

lineMarker
	: '#' INTEGER STRING INTEGER* ;


predicate
	: directive
	| refmode
	| singleAtom
	| atom
	| functional
	;

directive
	: predicateName '(' BACKTICK predicateName ')'
	| predicateName '[' BACKTICK predicateName ']' '=' expr
	;

refmode
	: predicateName AT_STAGE? '(' IDENTIFIER ':' expr ')' ;

singleAtom
	: predicateName AT_STAGE? '(' expr ')' ;

atom
	: predicateName AT_STAGE? '(' ')'
	| predicateName AT_STAGE? '(' expr ',' exprList ')'
	;

functionalHead
	: predicateName AT_STAGE? '[' exprList? ']' ;

functional
	: functionalHead '=' expr ;


aggregation
	: AGG '<<' IDENTIFIER '=' predicate '>>' compound ;

predicateList
	: predicate
	| predicateList ',' predicate
	;

compound
	: comparison
	| '!'? predicate
	| '!'? '(' compound ')'
	| compound ',' compound
	| compound ';' compound
	;


predicateName
	: '$'? IDENTIFIER
	| predicateName ':' IDENTIFIER
	;

constant
	: INTEGER
	| REAL
	| BOOLEAN
	| STRING
	;

expr
	: IDENTIFIER
	| functionalHead
	| constant
	| expr ('+' | '-' | '*' | '/') expr
	| '(' expr ')'
	;

exprList
	: expr
	| exprList ',' expr
	;

comparison
	: expr ('=' | '<' | '<=' | '>' | '>=' | '!=') expr ;


// Lexer

AGG
	: 'agg' ;

ALL
	: '*' ;

AS
	: 'as' ;

AT_STAGE
	: '@init'
	| '@initial'
	| '@prev'
	| '@previous'
	| '@past'
	;

BACKTICK
	: '`' ;

CAPACITY
	: '[' ('32' | '64' | '128') ']' ;

CMD
	: 'command' ;

COMP
	: 'component' ;

GLOBAL
	: '.' ;

L_BRACK
	: '{' ;

R_BRACK
	: '}' ;

INTEGER
	: [0-9]+
	| '0'[0-7]+
	| '0'[xX][0-9a-fA-F]+
	| '2^'[0-9]+
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
	: '"' ~["]* '"' ;

IDENTIFIER
	: [?]?[a-zA-Z_][a-zA-Z_0-9]* ;


LINE_COMMENT
	: '//' ~[\r\n]* -> skip ;

BLOCK_COMMENT
	: '/*' .*? '*/' -> skip ;

WHITE_SPACE
	: [ \t\r\n]+ -> skip ;
