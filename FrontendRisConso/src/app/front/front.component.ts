/*
 *  front.component.ts - DRIMBox
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

import { HttpClient, HttpEventType } from '@angular/common/http';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

@Component({
  selector: 'app-front',
  templateUrl: './front.component.html',
  styleUrls: ['./front.component.css']
})
export class FrontComponent implements OnChanges {

  constructor(private readonly http: HttpClient) { }

  // Input to retieve the structure chosen by the user
  @Input() selectedStruct = '';

  /**
   * Redirect to page with patient ins clicked
   * */
  goToUser1() {
    let body = '';
    // Check if a structure is selected
    if (this.selectedStruct != "") {
      body = 'ins=162018885045327&insAuthority=1.2.250.1.213.1.4.8&consent=yes&patientID=123&patientIDIssuer=1470003641&modality=CT&lastName=aa&firstName=aaa&birthDate=aaa&birthPlace=aaa&sex=aaa&situation=' + this.selectedStruct;
    }

    else
      body = 'ins=162018885045327&insAuthority=1.2.250.1.213.1.4.8&consent=yes&patientID=123&patientIDIssuer=1470003641&modality=CT&lastName=aa&firstName=aaa&birthDate=aaa&birthPlace=aaa&sex=aaa&toto=az';

    this.http.post('http://localhost:4200/drim', body, {
      responseType: 'text',
      observe: 'events'
    }).subscribe(
      response => {
        if (response.type === HttpEventType.Response) {
          console.log(response.url);
          window.open(response.url || "");

        }
      },
      error => {
        console.log("Error", error, body);
      },
      () => {
        console.log("POST is completed");
      });
  }

  goToUser2() {

    let body = 'ins=162018885045327&insAuthority=1.2.250.1.213.1.4.8&consent=yes&patientID=123&patientIDIssuer=1470003641&studyInstanceUID=2.16.840.1.113669.632.20.1211.10000502993';
    this.http.post('http://localhost:4200/drim', body, {
      responseType: 'text',
    }).subscribe(
      data => {
        window.open(data);
      },
      error => {
        console.log("Error", error, body);
      },
      () => {
        console.log("POST is completed");
      });
  }

  goToUser3() {
    let body = 'ins=162018885045327&insAuthority=1.2.250.1.213.1.4.8&consent=yes&patientID=123&patientIDIssuer=1470003641&anatomicRegion=Thorax';
    this.http.post('http://localhost:4200/drim', body, {
      responseType: 'text',
      observe: 'events'
    }).subscribe(
      response => {
        if (response.type === HttpEventType.Response) {
          console.log(response.url);
          window.open(response.url || "");

        }
      },
      error => {
        console.log("Error", error, body);
      },
      () => {
        console.log("POST is completed");
      });
  }

  goToUser4() {

    let body = 'ins=265075598096690&insAuthority=1.2.250.1.213.1.4.8&consent=yes&studyDate=20210123-20230123';
    this.http.post('http://localhost:4200/parameters', body, {
      responseType: 'text',
      observe: 'events'
    }).subscribe(
      response => {
        if (response.type === HttpEventType.Response) {
          console.log(response.url);
          window.open(response.url || "");

        }
      },
      error => {
        console.log("Error", error, body);
      },
      () => {
        console.log("POST is completed");
      });
  }

  goToUser5() {

    let body = 'ins=265075598096690&insAuthority=1.2.250.1.213.1.4.8&consent=yes&patientID=123&patientIDIssuer=1470003641&modality=CT&studyDate=20160910-20220910';
    this.http.post('http://localhost:4200/parameters', body, {
      responseType: 'text',
      observe: 'events'
    }).subscribe(
      response => {
        if (response.type === HttpEventType.Response) {
          console.log(response.url);
          window.open(response.url || "");

        }
      },
      error => {
        console.log("Error", error, body);
      },
      () => {
        console.log("POST is completed");
      });
  }


  /**
   * 
   * @param changes Update the structure chosen by the user
   */
  ngOnChanges(changes: SimpleChanges) {
    console.log(changes);
    this.selectedStruct = changes['selectedStruct'].currentValue;
  }

}
