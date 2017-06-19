grammar Datalog;

@header {
package org.clyze.deepdoop.datalog;
}

program
	: (component | cmd | initialize | propagate | datalog)* ;


component
	: COMP IDENTIFIER (':' IDENTIFIER)? '{' datalog* '}' (AS identifierList)? ;

cmd
	: CMD IDENTIFIER '{' datalog* '}' (AS identifierList)? ;

initialize
	: IDENTIFIER AS identifierList ;

propagate
	: IDENTIFIER '{' propagationList '}' '->' (IDENTIFIER | GLOBAL) ;

datalog
	: rightArrow | rightArrowBlock | leftArrow | lineMarker ;


rightArrow
    : annotationList? predicateName ('->' predicateName)? '.'
    | annotationList? compound '->' compound '.'
    | normalPredicate ',' refmode '->' normalPredicate '.'
    ;

rightArrowBlock
    : annotationList '{' rightArrow+ '}' ;

leftArrow
	: predicateListExt ('<-' compound)? '.'
	| functional '<-' aggregation '.'
	;


predicate
	: refmode
	| functional
	| normalPredicate
	;

refmode
	: predicateName AT_STAGE? '(' IDENTIFIER ':' expr ')' ;

functional
	: predicateName AT_STAGE? '[' exprList? ']' '=' expr ;

normalPredicate
	: predicateName AT_STAGE? '(' exprList? ')' ;

aggregation
	: AGG '<<' IDENTIFIER '=' predicate '>>' compound ;

construction
	: NEW '<<' functional AS predicateName '>>' ;

predicateListExt
	: (predicate | construction)
	| predicateListExt ',' (predicate | construction)
	;

compoundElement
	: predicate
	| comparison
	| '!' compoundElement
	| '(' compoundElement ')'
	;

compound
	: compoundElement
	| compound (',' | ';') compoundElement
	;

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

annotation
	: '@' IDENTIFIER ;

annotationList
	: annotation
	| annotationList ',' annotation
	;

lineMarker
	: '#' INTEGER STRING INTEGER* ;

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

NEW
	: 'new' ;

ALL
	: '*' ;

AS
	: [aA][sS] ;

AT_STAGE
	: '@init'
	| '@initial'
	| '@prev'
	| '@previous'
	| '@past'
	;

CAPACITY
	: '[' ('32' | '64' | '128') ']' ;

CMD
	: 'command' ;

COMP
	: 'component' ;

GLOBAL
	: '.' ;

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
