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

grammar Query;

options {
  ASTLabelType=CommonTree;
  output=AST;
}

tokens {
  // Synthetic tokens used for AST.
  ARGS;
  CONJUNCTION;
  DISJUNCTION;
  EMPTY;
  FUNCTION;
  FUZZY;
  GLOBAL;
  LITERAL;
  NEGATION;
  STRING;
  SEQUENCE;
  VALUE;
}

@header {
package com.google.appengine.api.search.query;
}

@lexer::header {
package com.google.appengine.api.search.query;
}

@parser::members {
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

// Do not fix errors in user queries, report them to user.
@rulecatch {
  catch (RecognitionException e) {
    reportError(e);
    throw e;
  }
}
@lexer::members {
private boolean exclamationNotFollowedByEquals() {
  if (input.LA(1) != '!') {
    throw new IllegalStateException();
  }
  return input.LA(2) != '=';
}

}

// -----------------------------------------------------------------------------
// Parser rules
// -----------------------------------------------------------------------------

// An optional expression terminated by EOF.
query
    : WS* EOF                -> ^(EMPTY)
    | WS* expression WS* EOF -> expression
    ;

// One or more sequences separated by the AND operator.
// E.g., foo bar AND baz
expression
    : sequence (
          /* empty */       -> sequence
        | (andOp sequence)+ -> ^(CONJUNCTION sequence+)
      )
    ;

// One or more factors separated by white space.
// E.g., foo bar
sequence
    : factor (
          /* empty */      -> factor
        | (WS+ factor)+    -> ^(SEQUENCE factor+)
      )
    ;

// One or more terms separated by the OR operator.
// E.g., foo OR bar OR baz
factor
    : term (
          /* empty */  -> term
        | (orOp term)+ -> ^(DISJUNCTION term+)
      )
    ;

term
  : primitive
  | notOp primitive -> ^(NEGATION primitive)
  ;

// Either a restriction (explicit, or implicit, global one),
// or a parenthesized, composite expression.
// E.g. user:jack
// E.g. jack
// E.g. (user:jack AND jack)
primitive
  : restriction
  | composite
  ;

// An implicit, global restriction, or an explicit comparison between
// a comparable and an arg.
// E.g., name:smith
// E.g., name:(smith OR kowalski)
restriction
  : comparable (
        /* empty */    -> ^(HAS GLOBAL comparable)
      | comparator arg -> ^(comparator comparable arg)
    )
  ;

// Comparator <, <=, >, >=, =, :
comparator
  : WS* (x=LE | x=LESSTHAN | x=GE | x=GT | x=NE | x=EQ | x=HAS) WS* -> $x
  ;

// Either a structured field name, or a function.
// E.g., name
// E.g., distance(location, geopoint(-33, 151))
comparable
  : member
  | function
  ;

member
    : item
    ;

// A function.
// E.g., distance(location, geopoint(-33, 151))
function
  : text LPAREN arglist RPAREN -> ^(FUNCTION text ^(ARGS arglist))
  ;

// A comma separated argument list.
// (20, "foo", geopoint(-33, 151))
arglist
  : /* empty */
  | arg (sep arg)* -> arg*
  ;

// Either an comparable value or another function.
arg
  : comparable
  | composite
  ;

// AND with at least one whitespace on each side.
andOp
  : WS+ AND WS+
  ;

// OR with at least one whitespace on each side.
orOp
  : WS+ OR WS+
  ;

// '-' or NOT followed by a space.
notOp
  : '-'
  | NOT WS+
  ;

// Comma with optional whitespace around it.
sep
  : WS* COMMA WS*
  ;

// Composite query built by surrounding expression with parenthesis.
// E.g., (foo OR bar:baz)
composite
  : LPAREN WS* expression WS* RPAREN -> expression
  ;

// Either plain or fuzzy value
// E.g., hello
// E.g., ~car
item
  : FIX value -> ^(LITERAL value)
  | REWRITE value -> ^(FUZZY value)
  | value -> value
  ;

// Either plain or quoted text.
value
  : text -> ^(VALUE TEXT text)
  | phrase -> ^(VALUE STRING phrase)
  ;

// Any sequence of non-special characters.
text
  : TEXT
  ;

// Any sequence of characters surrounded by quotes.
phrase
  : QUOTE ~QUOTE* QUOTE
  ;

// --- Lexer ---

HAS
  : ':'
  ;

OR
  : 'OR'
  ;

AND
  : 'AND'
  ;

NOT
  : 'NOT'
  ;

REWRITE
  : '~'
  ;

FIX
  : '+'
  ;

ESC
  : '\\' ('"' | '\\')
  | UNICODE_ESC
  | OCTAL_ESC
  ;

WS
  : (' ' | '\r' | '\t' | '\u000C' | '\n')
  ;

LPAREN
  : '('
  ;

RPAREN
  : ')'
  ;

COMMA
  : ','
  ;

// We use this indirectly in the ~QUOTE parser rule.
BACKSLASH
  : '\\'
  ;

// We don't use LT because it's defined as a macro in the ANTLR C runtime
// (which we used to use, though we don't any more).
LESSTHAN
  : '<'
  ;

GT
  : '>'
  ;

GE
  : '>='
  ;

LE
  : '<='
  ;

NE
  : '!='
  ;

EQ
  : '='
  ;

MINUS
  : '-'
  ;

QUOTE
  : '"'
  ;

TEXT
  : (START_CHAR | NUMBER_PREFIX | TEXT_ESC) (MID_CHAR | TEXT_ESC)*
  ;

// allows for capture of numbers, including floating point.
fragment NUMBER_PREFIX
  : MINUS? DIGIT
  ;

fragment TEXT_ESC
  : ESCAPED_CHAR
  | UNICODE_ESC
  | OCTAL_ESC
  ;

fragment UNICODE_ESC
    : '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;

fragment OCTAL_ESC
  : '\\' ('0'..'3') ('0'..'7') ('0'..'7')
  | '\\' ('0'..'7') ('0'..'7')
  | '\\' ('0'..'7')
  ;

fragment DIGIT
  : '0' .. '9'
  ;

fragment HEX_DIGIT
  : (DIGIT | 'a'..'f' | 'A'..'F')
  ;

// The last accepted code \uffee corresponds to the last Hangul character.
fragment START_CHAR
  : EXCLAMATION
  | '#' .. '\''
  | '*'
  | '.'
  | '/'
  | ';'
  | '?'
  | '@'
  | 'A' .. 'Z'
  | '['
  | ']' .. '}'
  | '\u00a1' .. '\uffee'
  ;

fragment MID_CHAR
  : START_CHAR
  | DIGIT
  | '+'
  | '-'
  ;

fragment ESCAPED_CHAR
  : '\\,' | '\\:' | '\\=' | '\\<' | '\\>' | '\\+' | '\\~' | '\\\"' | '\\\\'
  ;

fragment EXCLAMATION
  : { exclamationNotFollowedByEquals() }?=> '!'
  ;
