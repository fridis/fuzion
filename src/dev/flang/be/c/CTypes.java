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
 * Tokiwa Software GmbH, Germany
 *
 * Source of class CTypes
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.List;


/**
 * CTypes provides methods to handle C types
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CTypes extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  private final FUIR _fuir;


  /**
   * The C backend
   */
  private final CNames _names;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create instance of CTypes
   */
  public CTypes(FUIR fuir, CNames names)
  {
    this._fuir = fuir;
    this._names = names;
  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * The type of a value of the given clazz.
   */
  String clazz(int cl)
  {
    return _names.struct(cl) + (_fuir.clazzIsRef(cl) ? "*" : "");
  }


  /**
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz defining a type, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  boolean hasData(int cl)
  {
    return cl != -1 &&
      !_fuir.clazzIsUnitType(cl) &&
      !_fuir.clazzIsVoidType(cl) &&
      cl != _fuir.clazzUniverse();
  }


  /**
   * The type of a field.  This is the usually the same as clazz() of
   * the field's result clazz, except for outer refs for which
   * clazzFieldIsAdrOfValue, where it is a pointer to that type.
   */
  String clazzField(int cf)
  {
    var rc = _fuir.clazzResultClazz(cf);
    return (_fuir.clazzIsVoidType(rc)
            ? "struct { }"
            : clazz(rc)) + (_fuir.clazzFieldIsAdrOfValue(cf) ? "*" : "");
  }



  /**
   * Create declarations of the C types required for the given clazz.
   *
   * @param cl a clazz id.
   *
   * @return the C declaration or CStmnt.EMPTY if none.
   */
  CStmnt types(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Choice:
      case Routine:
        var name = _names.struct(cl);
        // special handling of stdlib clazzes known to the compiler
        var stype = scalar(cl);
        var type = stype != null ? stype : "struct " + name;
        return CStmnt.typedef(type, name);
      default:
        return CStmnt.EMPTY;
      }
  }


  /**
   * Does the given clazz specify a scalar type in the C code, i.e, standard
   * numeric types i32, u64, etc.
   */
  boolean isScalar(int cl)
  {
    return scalar(cl) != null;
  }


  /**
   * Check if the given clazz specifies a scalar type in the C code, i.e,
   * standard numeric types i32, u64, etc. If so, return that C type.
   *
   * @return the C scalar type corresponding to cl, null if cl is not scaler.
   */
  String scalar(int cl)
  {
    return
      _fuir.clazzIsI32(cl) ? "int32_t" :
      _fuir.clazzIsI64(cl) ? "int64_t" :
      _fuir.clazzIsU32(cl) ? "uint32_t" :
      _fuir.clazzIsU64(cl) ? "uint64_t" : null;
  }


  /**
   * Find the order in which the clazzes have to be declared to avoid C compiler
   * from complaining, i.e., all struct and union elements before the
   * surrounding structures.
   *
   * @return A list of all clazzes in the order they should be declared.
   */
  List<Integer> inOrder()
  {
    var result = new List<Integer>();
    var visited = new TreeSet<Integer>();
    for (var cl = _fuir.firstClazz(); cl <= _fuir.lastClazz(); cl++)
      {
        findDeclarationOrder(cl, result, visited);
      }
    return result;
  }


  /**
   * Helper routine for inOrder to check a given clazz.
   *
   * @param cl the clazz to check, will call itself recursively for all clazzes
   * cl depends on.
   *
   * @param result cl will be added to result after all the recursive calls for
   * clazzes cl depends on.
   *
   * @param visited tracks the clazzes this was called with already. If
   * visited.contains(cl), this will be a NOP.
   */
  private void findDeclarationOrder(int cl, List<Integer> result, TreeSet<Integer> visited)
  {
    if (!visited.contains(cl))
      {
        visited.add(cl);
        if (!isScalar(cl)) // special handling of stdlib clazzes known to the compiler
          {
            // first, make sure structs used for inner fields are declared:
            for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
              {
                var fcl = _fuir.clazzField(cl, i);
                var rcl = _fuir.clazzResultClazz(fcl);
                if (!_fuir.clazzIsRef(rcl) && !_fuir.clazzIsOuterRef(fcl))
                  {
                    findDeclarationOrder(rcl, result, visited);
                  }
              }
            for (int i = 0; i < _fuir.clazzNumChoices(cl); i++)
              {
                var cc = _fuir.clazzChoice(cl, i);
                if (cc != -1)
                  {
                    findDeclarationOrder(_fuir.clazzIsRef(cc) ? _fuir.clazzObject() : cc, result, visited);
                  }
              }
            if (_fuir.clazzIsRef(cl))
              {
                findDeclarationOrder(_fuir.clazzAsValue(cl), result, visited);
              }
          }
        result.add(cl);
      }
  }


  /**
   * Create declarations of the C structs required for the given clazz.  Write
   * code to cf.
   *
   * @param cl a clazz id.
   */
  CStmnt structs(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Choice:
      case Routine:
        {
          var l = new List<CStmnt>(CStmnt.lineComment("for " + _fuir.clazzAsString(cl)));
          var els = new List<CStmnt>();
          if (_fuir.clazzIsRef(cl))
            {
              var vcl = _fuir.clazzAsValue(cl);
              els.add(CStmnt.decl("uint32_t", _names.CLAZZ_ID));
              els.add(CStmnt.decl(clazz(vcl), _names.FIELDS_IN_REF_CLAZZ));
            }
          else if (_fuir.clazzIsChoice(cl))
            {
              var ct = _fuir.clazzChoiceTag(cl);
              if (ct != -1)
                {
                  els.add(CStmnt.decl(clazzField(ct), _names.TAG_NAME));
                }
              var uls = new List<CStmnt>();
              for (int i = 0; i < _fuir.clazzNumChoices(cl); i++)
                {
                  var cc = _fuir.clazzChoice(cl, i);
                  if (cc != -1 && !_fuir.clazzIsRef(cc))
                    {
                      uls.add(CStmnt.decl(clazz(cc), new CIdent(_names.CHOICE_ENTRY_NAME + i)));
                    }
                }
              if (_fuir.clazzIsChoiceWithRefs(cl))
                {
                  uls.add(CStmnt.decl(clazz(_fuir.clazzObject()), _names.CHOICE_REF_ENTRY_NAME));
                }
              els.add(CStmnt.unyon(uls, _names.CHOICE_UNION_NAME));
            }
          else
            {
              for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                {
                  var f = _fuir.clazzField(cl, i);
                  els.add(CStmnt.decl(clazzField(f), _names.fieldName(f)));
                }
            }
          l.add(CStmnt.struct(_names.struct(cl), els));
          if (cl == _fuir.clazzUniverse())
            {
              l.add(CStmnt.decl("static", _names.struct(cl), _names.UNIVERSE));
            }
          return CStmnt.seq(l);
        }
      default:
        break;
      }
    return CStmnt.EMPTY;
  }

}

/* end of file */
