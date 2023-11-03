/*
 *  check-access.service.ts - DRIMBox
 *
 * MIT License
 *
 * Copyright (c) 2022 b<>com
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class CheckAccessService {

  constructor(private readonly http: HttpClient) { }

  /**
   * Verify if patient dmp is open and user has right to access to it
   * @param ins of the patient user wants to access
   * @param opposition value 'yes' or 'no' -> give access if user don't have the right
   */
  async td0_2(ins: string, opposition: string, uuid: string): Promise<string> {
    if (opposition === "yes") {
      return "no access";
    }

    else if (opposition === "unknown") {
      return "unknown";
    }

    else {
      const response = await this.http.get('/api/conso/dmp/verify/' + ins + "?uuid=" + uuid, { responseType: 'text' }).toPromise();

      const verify = response.toString().split("AUTORISATION")[1].split("code=\"")[1].split("\"")[0];
      // If VALIDE, user has right to access to patient dmp
      if (verify === "VALIDE") {
        return "ok";
      }
      else if (opposition === "yes" && verify !== "INTERDIT") {
        // If content : yes, we add right for the user to access patient dmp
        return this.td0_3(ins);
      }
      else return "no access";
    }
  }

  /**
   * Give right to the user to access to patient dmp
   * @param ins of the patient
   */
  async td0_3(ins: string): Promise<string> {
    await this.http.get('/api/grant/' + ins, { responseType: 'text' }).toPromise();
    return "ok";
  }
}
