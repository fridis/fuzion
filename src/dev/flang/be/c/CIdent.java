/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa GmbH, Berlin
 *
 * Source of class CIdent
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;


/**
 * CIdent is a CExpr consisting of a single identifier
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
class CIdent extends CExpr
{


  /*----------------------------  variables  ----------------------------*/


  final String _name;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create a C expression from a plain identifier
   *
   * @return the resulting expression
   */
  CIdent(String name)
  {
    if (PRECONDITIONS) require
      (isAlphanumeric(name));

    this._name = name;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code corresponding to this ident
   *
   * @param sb will be used to append the code to
   */
  void code(StringBuilder sb) { sb.append(_name); }


  /**
   * The precedence of an ident
   *
   * @return 0
   */
  int precedence() { return 0; }

}

/* end of file */