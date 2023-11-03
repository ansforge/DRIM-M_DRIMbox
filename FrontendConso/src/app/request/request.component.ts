/*
 *  request.component.ts - DRIMBox
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

import { Component, OnInit, ElementRef, AfterViewInit } from '@angular/core';
import { FormControl, UntypedFormControl } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CheckAccessService } from './check-access.service';
import { DocumentsService } from './documents.service';
import { DatePipe } from '@angular/common';
import { MAT_DATE_FORMATS } from '@angular/material/core';
import { HttpClient } from '@angular/common/http';
import { empty } from 'rxjs';

// See the Moment.js docs for the meaning of these formats:
// https://momentjs.com/docs/#/displaying/format/
export const MY_FORMATS = {
  parse: {
    dateInput: 'DD/MM/YYYY',
  },
  display: {
    dateInput: 'DD/MM/YYYY',
    monthYearLabel: 'MMMM YYYY',
    dateA11yLabel: 'LL',
    monthYearA11yLabel: 'MMMM YYYY'
  },
};

@Component({
  selector: 'app-request',
  templateUrl: './request.component.html',
  styleUrls: ['./request.component.scss'],
  providers: [
    // `MomentDateAdapter` can be automatically provided by importing `MomentDateModule` in your
    // application's root module. We provide it at the component level here, due to limitations of
    // our example generation script.
    { provide: MAT_DATE_FORMATS, useValue: MY_FORMATS },
  ],
})
export class RequestComponent implements OnInit, AfterViewInit {

  uuid: string;

  // boolean to display error message
  accessError = false;
  accessUnknown = false;

  // opposition value retrieve in url parameter
  opposition: string;
  // ins retrieved in url parameter
  ins: string;

  accessionNumber: string;

  regions = new UntypedFormControl('');
  regionsList: string[] = ['Abdomen + pelvis', 'Corps entier', 'Membres superieurs', 'Membres inferieurs', 'Tete', 'Thorax'];

  modalites = new UntypedFormControl('');
  modalitesList: string[] = ['CT', 'US', 'MR', 'PT'];

  startDate = new UntypedFormControl();
  stopDate = new UntypedFormControl();

  // table headers
  headElements = ['Examens', 'Date', 'CR', 'Visionneuse', 'PACS'];
  headElementsPatient = ['Nom', 'Prenom', 'Sexe', 'Date de naissance', 'INS'];
  headSerieElemKOS = ['Description', 'Localisation', 'Modalité', 'Nombre d\images'];

  /**
   * Change background color to black on ins page
   * */
  ngAfterViewInit() {
    this.elementRef.nativeElement.ownerDocument.body.style.backgroundColor = '#000';
    this.elementRef.nativeElement.ownerDocument.body.style.color = '#fff';
  }

  /**
   *
   * @param route to manage angular routes
   * @param checkAccessService to check-access component
   * @param documentsService to documents component
   * @param elementRef to change css
   */
  constructor(private readonly route: ActivatedRoute, private readonly http: HttpClient, private readonly checkAccessService: CheckAccessService,
    public documentsService: DocumentsService, private readonly elementRef: ElementRef) {
  }

  /**
   * On Init, retrieve url param and check auth and right to access
   * */
  async ngOnInit() {
    this.route.queryParams.subscribe(async params => {
      this.uuid = params['uuid'];
      let auth = "";
      if (this.ins === undefined && this.uuid !== undefined) {
        this.ins = await this.retrieveINS();
        this.opposition = await this.retrieveOpposition();
        auth = await this.checkAccessService.td0_2(this.ins, this.opposition, this.uuid);
      }


      if (auth === "ok") {
        await this.checkFilters();

        this.filterSearch();
      }
      else if (auth === "no access") {
        this.accessError = true;
      }
      else if (auth === "unknown") {
        this.accessUnknown = true;
      }
    });
  }

  async retrieveINS() {
    const response = await this.http.get('/api/conso/parameters/ins?uuid=' + this.uuid, { responseType: 'text' }).toPromise();
    return response;
  }

  async retrieveOpposition() {
    const response = await this.http.get('/api/conso/parameters/opposition?uuid=' + this.uuid, { responseType: 'text' }).toPromise();
    return response;
  }

  async checkFilters() {
    const response = await this.http.get('/api/conso/parameters/filter?uuid=' + this.uuid, { responseType: 'text' }).toPromise();

    if (response.includes("Modality")) {
      this.modalites.setValue([response.split("Modality=")[1].split("/")[0]]);
    }
    if (response.includes("anatomicRegion")) {
      this.regions.setValue([response.split("anatomicRegion=")[1].split("/")[0]]);
    }
    if (response.includes("accessionNumber")) {
      this.accessionNumber = response.split("accessionNumber=")[1].split("/")[0];
    }
    if (response.includes("studyDate")) {

      const dates = response.split("studyDate=")[1].split("/")[0];

      if (dates.includes("-")) {
        // this.stopDate.setValue([new DatePipe('en').transform(dates.split("-")[1], 'dd/MM/YYYY')]);
        const endDate = dates.split("-")[1];
        this.stopDate = new FormControl(new Date(endDate.substr(0, 4) + "-" + endDate.substr(4, 2) + "-" + endDate.substr(6)));

        if (dates.includes("-")) {
          const beginDate = dates.split("-")[0];
          this.startDate = new FormControl(new Date(beginDate.substr(0, 4) + "-" + beginDate.substr(4, 2) + "-" + beginDate.substr(6)));

        }
      }
      else {
        this.startDate = new FormControl(new Date(dates.substr(0, 4) + "-" + dates.substr(4, 2) + "-" + dates.substr(6)));
      }

    }
  }

  /**
   * Check filters after research button click
   * */
  filterSearch() {
    const startDateFormat = new DatePipe('en').transform(this.startDate.value, 'yyyyMMdd');
    const stopDateFormat = new DatePipe('en').transform(this.stopDate.value, 'yyyyMMdd');

    // Check if filters selected, if not classic request
    if (this.modalites.value.length === 0 && this.regions.value.length === 0 && startDateFormat === null && stopDateFormat === null && this.accessionNumber === undefined) {
      this.documentsService.td3_1(this.ins);
    }
    // Request with filters
    else {
      this.documentsService.td3_1Filters(this.ins, this.modalites.value, this.regions.value, startDateFormat, stopDateFormat, this.accessionNumber);
    }
  }

}

