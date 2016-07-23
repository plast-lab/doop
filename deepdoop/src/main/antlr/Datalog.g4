grammar Datalog;

@header {
package deepdoop.datalog;
}

program
	: (comp | init_ | propagate | cmd | datalog)* ;


comp
	: COMP IDENTIFIER (':' IDENTIFIER)? '{' datalog* '}' ;

init_
	: IDENTIFIER '=' NEW IDENTIFIER ;

propagate
	: IDENTIFIER '{' (ALL | predicateNameList) '}' PROPAGATE (IDENTIFIER | GLOBAL) ;

predicateNameList
	: predicateName
	| predicateNameList ',' predicateName
	;

cmd
	: CMD IDENTIFIER '{' datalog* '}' ;


datalog
	: declaration | constraint | rule_ ;

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


predicate
	: predicateName (CAPACITY | AT_STAGE)? '(' (exprList | BACKTICK predicateName)? ')'
	| predicateName             AT_STAGE?  '[' (exprList | BACKTICK predicateName)? ']' '=' expr
	| refmode
	;

refmode
	: predicateName AT_STAGE? '(' IDENTIFIER ':' expr ')' ;

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
	| predicateName AT_STAGE? '[' exprList? ']'
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

AGG
	: 'agg' ;

ALL
	: '*' ;

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
	: '.global' ;

NEW
	: 'new' ;

PROPAGATE
	: 'copyto' ;

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
