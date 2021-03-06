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
 * Source of class Backend
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;  // NYI: move to dev.flang.fuir?

import dev.flang.util.ANY;


/**
 * Backend gives an abstract view of the Backend as seen from the Fuzion IR.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Backend extends ANY
{

  /**
   * Obtain backend information required for dynamic binding lookup to perform a
   * call.
   *
   * @param dynamic true if this sets the static inner / outer clazz of a
   * dynamic call, false if this is a static call
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @param outerClazz the static clazz of the target instance of this call
   *
   * @return a beckend-specific object.
   */
  public abstract BackendCallable callable(boolean dynamic,
                                           Clazz innerClazz,
                                           Clazz outerClazz);

}

/* end of file */
