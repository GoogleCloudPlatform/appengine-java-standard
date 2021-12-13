/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

grammar Expression;

// OPTIONS is a template variable to allow language specific options
// to be added when generating the Expression.g file.
options {
  output=AST;
  ASTLabelType=CommonTree;
}

tokens {
  NEG;
  INDEX;
}

// HEADER is a template variable to allow language specific header
// information to be added when generating the Expression.g file.
@header {
  package com.google.appengine.api.search.query;
}

@lexer::header {
  package com.google.appengine.api.search.query;
}

@members {
  @Override
  public Object recoverFromMismatchedSet(IntStream input,
      RecognitionException e, BitSet follow) throws RecognitionException {
    throw e;
  }

  @Override
  protected Object recoverFromMismatchedToken(
      IntStream input, int ttype, BitSet follow) throws RecognitionException {
    throw new MismatchedTokenException(ttype, input);
  }
}

// Do not fix errors in user queries, report to user.
@rulecatch {
  catch (RecognitionException e) {
    reportError(e);
    throw e;
  }
}


expression
  : conjunction EOF
  ;

condExpr
  : conjunction (COND^ addExpr)?
  ;

conjunction
  : disjunction (AND^ disjunction)*
  ;

disjunction
  : negation ((OR | XOR)^ negation)*
  ;

negation
  : cmpExpr
  | NOT^ cmpExpr
  ;

cmpExpr
  : addExpr (cmpOp^ addExpr)?
  ;

cmpOp
  : LT
  | LE
  | GT
  | GE
  | EQ
  | NE
  ;

addExpr
  : multExpr (addOp^ multExpr)*
  ;

addOp
  : PLUS
  | MINUS
  ;

multExpr
  : unary (multOp^ unary)*
  ;

multOp
  : TIMES
  | DIV
  ;

unary
  : MINUS atom -> ^(NEG["-"] atom)
  | atom
  ;

atom
  : var
  | num
  | str
  | fn
  | LPAREN conjunction RPAREN -> conjunction
  ;

var
  : name
  | name index -> ^(INDEX[$index.text] name)
  ;

index
  : LSQUARE x=INT RSQUARE -> $x
  ;

name
  : NAME ('.'^ NAME)*
  // We include the following keywords as legal identifiers
  // to maintain backwards-compatibility.
  | t=TEXT -> NAME[$t]
  | t=HTML -> NAME[$t]
  | t=ATOM -> NAME[$t]
  | t=DATE -> NAME[$t]
  | t=NUMBER -> NAME[$t]
  | t=GEO -> NAME[$t]
  | t=GEOPOINT -> NAME[$t]
  ;

num
  : INT
  | FLOAT
  ;

str
  : PHRASE
  ;

fn
  : fnName LPAREN condExpr (COMMA condExpr)* RPAREN
      -> ^(fnName condExpr+)
  ;

fnName
 : ABS
 | COUNT
 | DISTANCE
 | GEOPOINT
 | LOG
 | MAX
 | MIN
 | POW
 | SNIPPET
 | SWITCH
 | TEXT
 | HTML
 | ATOM
 | DATE
 | NUMBER
 | GEO
 | DOT
 | VECTOR
 ;

ABS
  : 'abs'
  ;

COUNT
  : 'count'
  ;

DISTANCE
  : 'distance'
  ;

GEOPOINT
  : 'geopoint'
  ;

LOG
  : 'log'
  ;

MAX
  : 'max'
  ;

MIN
  : 'min'
  ;

POW
  : 'pow'
  ;

AND
  : 'AND'
  ;

OR
  : 'OR'
  ;

XOR
  : 'XOR'
  ;

NOT
  : 'NOT'
  ;

SNIPPET
 : 'snippet'
 ;

SWITCH
  : 'switch'
  ;

TEXT
  : 'text'
  ;

HTML
  : 'html'
  ;

ATOM
  : 'atom'
  ;

DATE
  : 'date'
  ;

NUMBER
  : 'number'
  ;

GEO
  : 'geo'
  ;

DOT
  : 'dot'
  ;

VECTOR
  : 'vector'
  ;

INT
  : DIGIT+
  ;

PHRASE
  :  QUOTE (ESC_SEQ | ~('"'|'\\'))* QUOTE
  ;

FLOAT
  : (DIGIT)+ '.' (DIGIT)* EXPONENT?
  | '.' (DIGIT)+ EXPONENT?
  | (DIGIT)+ EXPONENT
  ;

NAME
  : NAME_START (NAME_START | DIGIT)*
  ;

LPAREN
  : '('
  ;

RPAREN
  : ')'
  ;

LSQUARE
  : '['
  ;

RSQUARE
  : ']'
  ;

PLUS
  : '+'
  ;

MINUS
  : '-'
  ;

TIMES
  : '*'
  ;

DIV
  : '/'
  ;

LT
  : '<'
  ;

LE
  : '<='
  ;

GT
  : '>'
  ;

GE
  : '>='
  ;

EQ
  : '='
  ;

NE
  : '!='
  ;

COND
  : '?'
  ;

QUOTE
  : '"'
  ;

COMMA
  : ','
  ;

WS
  : (' ' | '\t' | '\n' | '\r')+ {$channel = HIDDEN;}
  ;

fragment EXPONENT
  : ('e'|'E') ('+'|'-')? (DIGIT)+
  ;

fragment NAME_START
  : (ASCII_LETTER | UNDERSCORE | DOLLAR)
  ;

fragment ASCII_LETTER
  : 'a' .. 'z'
  | 'A' .. 'Z'
  ;

fragment DIGIT
  : '0' .. '9'
  ;

fragment DOLLAR
  : '$'
  ;

fragment UNDERSCORE
  : '_'
  ;

fragment HEX_DIGIT
  : ('0'..'9'|'a'..'f'|'A'..'F')
  ;

fragment ESC_SEQ
  : '\\' ('b'|'t'|'n'|'f'|'r'|'"'|'\''|'\\')
  | UNICODE_ESC
  | OCTAL_ESC
  ;

fragment OCTAL_ESC
  : '\\' ('0'..'3') ('0'..'7') ('0'..'7')
  | '\\' ('0'..'7') ('0'..'7')
  | '\\' ('0'..'7')
  ;

fragment UNICODE_ESC
  :  '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
  ;
