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
 * Source of class FrontEndOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;


/**
 * FrontEndOptions specify the configuration of the front end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FrontEndOptions extends FuzionOptions
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Read code from stdin?
   */
  final boolean _readStdin;


  /**
   * Read code from file?
   */
  final Path _inputFile;


  /**
   * main feature name, null iff _readStdin
   */
  final String _main;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Costructor initializing fields as given.
   */
  public FrontEndOptions(int verbose, boolean fuzionSafety, int fuzionDebugLevel, boolean readStdin, String main)
  {
    super(verbose,
          fuzionSafety,
          fuzionDebugLevel);

    if (PRECONDITIONS) require
                         (verbose >= 0,
                          readStdin || main != null,
                          !readStdin || main == null);

    _readStdin = readStdin;
    var p = main != null ? Path.of(main).toAbsolutePath() : null;
    if (p != null && main.toUpperCase().endsWith(".FZ") && Files.exists(p))
      {
        _inputFile = p;
        _main = null;
      }
    else
      {
        _inputFile = null;
        _main = main;
      }
  }


  /*-----------------------------  methods  -----------------------------*/

}

/* end of file */
