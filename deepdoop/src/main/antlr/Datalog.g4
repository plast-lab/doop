grammar Datalog;

@header {
package deepdoop.datalog;
}

program
	: (declaration | constraint | rule_ /*| directive*/)* ;

declaration
	: predicate '->' predicateList? '.'
	| predicateName '(' IDENTIFIER ')' ',' refmode '->' predicate '.'
	;

constraint
	: ruleBody '->' ruleBody '.' ;

rule_
	: predicateList ('<-' ruleBody?)? '.'
	| predicate '<-' aggregation '.'
	;

//directive
//	: predicateName '(' '`' predicateName ')' '.'
//	| predicateName '[' ('`' predicateName)? ']' '=' constant '.'
//	;

predicate
	//: (ADD | RM)? predicateName ('@' AT_SUFFIX)? CAPACITY? '(' exprList? ')'
	: (ADD | RM)? predicateName ('@' AT_SUFFIX)? '(' exprList? ')'
	| (ADD | RM | UP)? functionalHead '=' expr
	| (ADD | RM)? refmode
	| predicateName CAPACITY '(' IDENTIFIER ')'
	| predicateName '(' BACKTICK predicateName ')'
	| predicateName '[' BACKTICK predicateName ']' '=' constant
	;

ruleBody
	: predicate
	| comparison
	| '(' ruleBody ')'
	| ruleBody ',' ruleBody
	| ruleBody ';' ruleBody
	| '!' ruleBody
	;

aggregation
	: AGG '<<' IDENTIFIER '=' predicate '>>' ruleBody ;

refmode
	: predicateName ('@' AT_SUFFIX)? '(' IDENTIFIER ':' expr ')' ;

functionalHead
	: predicateName ('@' AT_SUFFIX)? '[' exprList? ']' ;

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

comparison
	: expr ('=' | '<' | '<=' | '>' | '>=' | '!=') expr ;

predicateList
	: predicate
	| predicateList ',' predicate
	;

exprList
	: expr
	| exprList ',' expr
	;



// Lexer

AT_SUFFIX
	: 'init'
	| 'initial'
	| 'prev'
	| 'previous'
	| 'past'
	;

CAPACITY
	: '[' ('32' | '64' | '128') ']' ;

BACKTICK
	: '`' ;

ADD
	: '+' ;
RM
	: '-' ;
UP
	: '^' ;

AGG
	: 'agg' ;

INTEGER
	: [0-9]+
	| '0'[0-7]+
	| '0'[xX][0-9a-fA-F]+
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
