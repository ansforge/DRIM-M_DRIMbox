/*
 *  documents.service.ts - DRIMBox
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

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import * as dicomParser from 'dicom-parser';

/**
 * Enum for mimeTypes
 * */
enum mimeTypeEnum {
  dicom = 'application/dicom',
  xml = 'text/xml'
}

@Injectable({
  providedIn: 'root'
})

export class DocumentsService {

  // List of kos docs (mimeType = application/dicom)
  kosDocs = [{
    uniqueId: '',
    repositoryId: '',
    modalite: '',
    region: '',
    display: false,
    retrieveURL: '',
    accessionNumber: '',
    mimeType: '',
    sopInstance: '',
    series: [{
      serieDescription: '',
      nbImages: '',
      serieModalite: '',
      serieLocation: '',
      retrieveURL: ''
    }]
  }];

  // List of cda docs (mimeType = text/xml)
  cdaDocs = [
    {
      uniqueId: '',
      repositoryId: '',
      mimeType: '',
      description: '',
      dateExam: '',
      modalite: '',
      region: '',
      retrieveURL: '',
      display: false,
      accessionNumber: '',
      sopInstance: '',
      refKos: [{
        kos: this.kosDocs
      }],
      rapports: [{
        pdfValue: '',
        auteur: '',
        description: ''
      }]
    }
  ];


  // array with patient infos
  infoPatient = {
    nom: '',
    prenom: '',
    sexe: '',
    dateNaissance: '',
    ins: ''
  };
  disp = false;

  // Number of cda docs retrieved from dmp
  nbDocs = "0";
  // Boolean to display error structure panel
  sectError = false;
  // Ins of patient retrieved fro url param
  ins: string;
  // Value of last study opened
  studyInstanceLocal = '';
  // Value of last serie opened
  serieInstanceLocal = '';
  // Boolean indicates if import panel opened from serie or study level
  openImportSerie = false;

  /**
   * Constructor
   * @param http for rest requests
   */
  constructor(private readonly http: HttpClient) {
    this.cdaDocs = [];
    this.kosDocs = [];
  }

  /**
   * Return number of cda docs
   * */
  getNbDocuments() {
    return this.cdaDocs.length;
  }

  /**
   * Initiate 3_1 request to retrieve list of documents from patient
   * @param ins of the patient
   */
  td3_1(ins: string) {
    this.ins = ins;
    // Clear list of documents
    this.cdaDocs = [];
    this.kosDocs = [];
    // Get request to backend
    this.http.get(`/api/query/${ins}`, { responseType: 'text' }).subscribe(data => {
      this.parsing3_1Response(data);
    },
      err => {
        // check error status code is 500, if so, display error
        this.sectError = true;
      });
  }

  /**
 * Initiate 3_1 request to retrieve list of documents filters from patient
 * @param ins of the patient
 */
  td3_1Filters(ins: string, modalities: string[], regions: string[], startDate: string, stopDate: string, accessionNumber: string) {
    this.ins = ins;
    // Clear list of documents
    this.kosDocs = [];
    this.cdaDocs = [];
    // Get request to backend
    let filterparam = "";
    if (modalities.length !== 0) {
      modalities.forEach(function (modality) {
        filterparam += `&modality=${modality}`;
      });
    }

    if (regions.length !== 0) {
      regions.forEach(function (region) {
        filterparam += `&region=${region}`;
      });
    }

    if (startDate !== null) {
      filterparam += `&start=${startDate}0000`;
    }

    if (stopDate !== null) {
      filterparam += `&stop=${stopDate}0000`;
    }

    if (accessionNumber !== undefined) {
      filterparam += `&accessionNumber=${accessionNumber}`;
    }

    this.http.get(`/api/query/${ins}?${filterparam.substr(1)}`, { responseType: 'text' }).subscribe(data => {
      this.parsing3_1Response(data);
    },
      err => {
        // check error status code is 500, if so, display error
        this.sectError = true;
      });

  }

  /**
   * Parse 3_1 response to retrieve documents info from patient
   * @param response 3_1 request response from dmp
   */
  parsing3_1Response(response: string) {
    console.log(response);
    // Verify if response is Success
    const reqStatus = response.split("ResponseStatusType:")[1].split("\"")[0];
    if (reqStatus === "Success") {
      // Get number of documents
      this.nbDocs = response.split("totalResultCount=\"")[1].split("\"")[0];
      // Fill Patient Info
      // Check if patient has documents
      if (this.nbDocs !== "0") {
        this.fillInfoPatient(response);
        // Retrieve common repositoryId
        const repositoryId = response.split("repositoryUniqueId")[1].split("Value>")[1].split("</")[0];

        for (let i = 0; i < Number(this.nbDocs); i++) {
          // Retrieve informations of each document
          const metadataDoc = response.split("<ns4:ExtrinsicObject")[i + 1].split("</ns4:ExtrinsicObject>")[0];
          this.retrieveInformations(metadataDoc, repositoryId);
        }

        // Sort CDA by exam date (more recent first)
        this.cdaDocs.sort((a, b) => {
          if (a.dateExam < b.dateExam) return 1;
          else if (a.dateExam > b.dateExam) return -1;
          else return 0;
        });

        // Link accession number between Cda and Kos documents
        this.cdaDocs.forEach((cda) => {
          cda.dateExam = `${cda.dateExam.substr(6, 2)}/${cda.dateExam.substr(4, 2)}/${cda.dateExam.substr(0, 4)} ${cda.dateExam.substr(8, 2)}:${cda.dateExam.substr(10, 2)}:${cda.dateExam.substr(12, 2)}`;
          this.FindKosForCda(cda);
          if (cda.refKos.length !== 0) {
            cda.description = `${cda.modalite} / ${cda.region}`;
          }
        });
      }
    }
  }


  /**
   * Fill patient infos from response
   * @param response 3_1 request response from dmp
   */
  fillInfoPatient(response) {
    this.infoPatient.dateNaissance = "01/01/1950";
    this.infoPatient.nom = response.split("sourcePatientInfo")[1].split("PID-5|")[1].split("^")[0];
    this.infoPatient.prenom = response.split("sourcePatientInfo")[1].split("PID-5|")[1].split("^")[1];
    this.infoPatient.sexe = response.split("sourcePatientInfo")[1].split("PID-8|")[1].split("<")[0];
    this.infoPatient.ins = this.ins;
  }

  /**
   * Find for each cda the kos with the same accession number
   * */
  FindKosForCda(cda) {
    // Sort documents to have cda in first
    this.kosDocs.forEach(function (kosDoc) {
      if (kosDoc.accessionNumber === cda.accessionNumber) {
        cda.refKos.push({ kos: kosDoc });
        cda.sopInstance = kosDoc.sopInstance;
        kosDoc.modalite.split("et ").forEach(function (modal) {
          if (cda.modalite.search(modal) === -1) {
            cda.modalite += "et " + modal;
          }
        });

        if (cda.region.search(kosDoc.region) === -1) {
          cda.region += "et " + kosDoc.region;
        }
      }
    });
    cda.modalite = cda.modalite.substr(3);
    cda.region = cda.region.substr(3);
  }

  /**
   * Parse metadatas to retrieve infos from cda and kos
   * @param metadataDoc for one document
   * @param repoId repositoryId
   */
  retrieveInformations(metadataDoc: string, repoId: string) {
    const documentId = metadataDoc.split("XDSDocumentEntry.patientId")[1].split("value=\"")[1].split("\"")[0];
    const mimeTypeValue = metadataDoc.split("mimeType=\"")[1].split("\"")[0];
    let descriptionValue = '';
    if (metadataDoc.search("<ns4:Name>") !== -1) {
      descriptionValue = metadataDoc.split("<ns4:Name>")[1].split("value=\"")[1].split("\"")[0];
    }
    let sopInstance = "";

    sopInstance = metadataDoc.split("urn:uuid:2e82c1f6-a085-4c72-9da3-8640a32e42ab")[1].split("value=\"")[1].split("\"")[0];

    const dateExamValue = metadataDoc.split("creationTime")[1].split("Value>")[1].split("<")[0];
    const access = metadataDoc.split("referenceIdList")[1].split("</ns4:ValueList>")[0].split("<ns4:Value>");
    const retrieveUrl = metadataDoc.split("uniqueId")[0].split("referenceIdList")[1].split("<ns4:Value>");
    let accessi = '';
    let retri = '';
    // Retrieve accessionNumber
    for (const x of access) {
      if (x.includes("accession")) {
        accessi = x.split("^")[0];
      }
    }
    // Retrieve retrieveUrl
    for (const y of retrieveUrl) {
      if (y.includes("studyInstanceUID")) {
        retri = y.split("^")[0];
      }
    }
    // Add cda document
    if (mimeTypeValue === mimeTypeEnum.xml) {
      const temp = {
        uniqueId: documentId,
        repositoryId: repoId,
        mimeType: mimeTypeValue,
        description: descriptionValue,
        dateExam: dateExamValue,
        modalite: '',
        region: '',
        retrieveURL: retri,
        display: false,
        accessionNumber: accessi,
        sopInstance: '',
        refKos: [{
          kos: null
        }],
        rapports: [{
          pdfValue: '',
          auteur: '',
          description: ''
        }]
      }

      // Pop kos ref and rapports from temp
      temp.refKos.pop();
      temp.rapports.pop();
      // Add document in our cda document list
      this.cdaDocs.push(temp);
    }

    // Add kos document
    else if (mimeTypeValue === mimeTypeEnum.dicom) {
      let modal = '';
      let region = '';

      // Retrieve list of modality and regions from metadata
      metadataDoc.split("urn:uuid:2c6b8cb7-8b2a-4051-b291-b1ae6a575ef4").forEach(function (event) {

        if (event.split("<ns4:Value>")[1].split("</ns4:Value>")[0] === "1.2.250.1.213.1.1.5.618" && modal.search(event.split("nodeRepresentation=\"")[1].split("\"")[0]) === -1) {
          modal += `et ${event.split("nodeRepresentation=\"")[1].split("\"")[0]}  `;
        }

        else if (event.split("<ns4:Value>")[1].split("</ns4:Value>")[0] === "1.2.250.1.213.1.1.5.695" && region.search(event.split(" value=\"")[1].split("\"")[0]) === -1) {
          region += `et ${event.split("value=\"")[1].split("\"")[0]}  `;
        }

      });
      const temp = {
        uniqueId: documentId,
        repositoryId: repoId,
        modalite: modal.substr(3),
        region: region.substr(3),
        display: false,
        retrieveURL: retri,
        accessionNumber: accessi,
        mimeType: mimeTypeValue,
        sopInstance: sopInstance,
        series: [{
          serieDescription: '',
          nbImages: '',
          serieModalite: '',
          serieLocation: '',
          retrieveURL: ''
        }]
      }

      temp.series.pop();
      // Adding kos document in array
      this.kosDocs.push(temp);
    }
  }

  /**
   * Show or hide study array
   * @param doc
   */
  dispCda(doc) {
    doc.display = !doc.display;
  }



  /**
   * Initiate 3_2 request to retrieve specific document (cda or kos) from dmp
   * @param doc document we want to retrieve
   * @param type indicates if we are on study or serie level
   */
  td3_2(doc, type: string) {
    // If serie level
    if (type === "serie") {
      // Hide or show the serie
      doc.display = !doc.display;
      // Verify we didn't already import the kos
      // Get request to backend to retrieve kos associated to cda accession number
      this.http.get(`/api/retrieve/${this.ins}?repositoryId=${doc.repositoryId}&uniqueId=${doc.uniqueId}`,
        { responseType: 'arraybuffer' as 'json' }).subscribe(data => {
          // Parsing kos received
          this.parsing3_2Response(data, type, doc);
        });
    }
    // If study level
    // Verify we didn't already import the cda
    else if (type === "study" && doc.rapports.length === 0) {
      // Get request to backend to retrieve cda
      this.http.get(`/api/retrieve/${this.ins}?repositoryId=${doc.repositoryId}&uniqueId=${doc.uniqueId}`,
        { responseType: 'arraybuffer' as 'json' }).subscribe(data => {
          // Parsing cda received
          this.parsing3_2Response(data, type, doc);
        });
    }
  }

  /**
   * Parsing 3_2 response from dmp
   * @param response given by dmp
   * @param type indicates if we are on study or serie level
   * @param doc document we want to retrieve
   * @param docCDA document cda link we the kos
   */
  parsing3_2Response(response, type: string, doc) {
    // if Kos
    if (doc.mimeType === mimeTypeEnum.dicom) {
      if (doc.series.length === 0) {
        this.parsingKos(response, type, doc)
      }
    }
    // If Cda
    else if (doc.mimeType === mimeTypeEnum.xml) {
      this.parsingCda(response, doc, type);
    }
  }

  /**
   * Parse kos retrieved
   * @param response given by dmp
   * @param type indicates if we are on study or serie level
   * @param doc document we want to retrieve
   */
  parsingKos(response, type, doc) {
    // Convert byteArray response to string
    const responseString = String.fromCharCode.apply(null, new Uint8Array(response));
    // Find start of kos
    const startKOS = responseString.search('DICM') - 128;
    response = response.slice(startKOS);
    const byteArray = new Uint8Array(response);
    try {
      // Parse the byte array to get a DataSet object that has the parsed contents
      const dataSet = dicomParser.parseDicom(byteArray);
      if (type === "serie") {
        const desc = dataSet.string('x0040a160');
        doc.description = dataSet.string('x00081030');
        doc.sopInstance = dataSet.string('x00080018');
        const dataSetSeries = dataSet.elements['x0040a375'].items[0].dataSet.elements['x00081115'].items;
        // Fill serie infos with kos
        dataSetSeries.forEach(function (serie) {
          doc.series.push({
            serieDescription: desc.split(serie.dataSet.string('x0020000e') + ' : ')[1].split(' : ')[1].split('\n')[0],
            nbImages: serie.dataSet.elements['x00081199'].items.length,
            serieModalite: desc.split(serie.dataSet.string('x0020000e') + ' :')[1].split(' : ')[0],
            serieLocation: 'Tête',
            retrieveURL: serie.dataSet.string('x00081190')
          });
        }, this);
      }
    }
    catch (ex) {
      console.log('Error parsing byte stream', ex);
    }

    // Sort kos by serieDescription
    doc.series.sort((a, b) => {
      if (a.serieDescription < b.serieDescription) return 1;
      else if (a.serieDescription > b.serieDescription) return -1;
      else return 0;
    });

  }

  /**
   * Parsing cda document and retrieve pdf
   * @param response given by dmp
   * @param doc document we want to retrieve
   * @param type indicates if we are on study or serie level
   */
  parsingCda(response, doc, type) {
    // First we need to convert the request into a string so we can extract the PDF in base64
    const blob = new Blob([new Uint8Array(response)], { type: 'text/plain; charset=utf-8' });
    blob.text().then(text => {
      if (text.search("application/pdf") !== -1) {
        // Indexes
        const firstPDFTag = "representation=\"B64\">";
        // We need to apply an offset to select the first char of the base64 str
        const firstPDFCharIndex = text.search(firstPDFTag) + firstPDFTag.length;
        const lastPDFCharIndex = text.search("</text>");
        // Slice base64 PDG
        const strPdfFile = text.slice(firstPDFCharIndex, lastPDFCharIndex);
        // Open raw data
        //window.open("data:application/pdf;base64, " + encodeURI(strPdfFile))
        doc.rapports.push({
          pdfValue: strPdfFile,
          auteur: `${text.split("<author>")[1].split("<family")[1].split("</family>")[0]} ${text.split("<author>")[1].split("<given>")[1].split("</given>")[0]}`,
          description: text.split("<title>")[1].split("</title>")[0]
        });
        // Or make it in a embed element if you prefer
        if (type === "study") {
          this.openPdf(doc);
        }
      }
    });
  }

  /**
   * Open window to ohif to display study/series
   * @param retrieveURL of the study/series
   * @param doc from the study/series
   */
  goToViewer(retrieveURL, sopInstance) {
    // Need this to get auth ohif beforehand (transmit the cookie value to the backend)
    // TODO : Change this and pass the cookie to the OHIF metadata (see backend for details)
    let urlRetrieve = retrieveURL;
    console.log("527 : " + sopInstance);
    const drimboxConso = "localhost:8082";
    const drimboxSource = "172.17.185.143:8083";
    const viewerURL = "localhost:3000";
    if (retrieveURL.includes("series")) {
      urlRetrieve = retrieveURL.split("/studies/")[1].split("/series")[0];
    }
    window.open(`http://${viewerURL}/viewer/dicomjson?url=http://${drimboxConso}/ohifv3metadata/${drimboxSource}/${urlRetrieve}/${sopInstance}`, '_blank');
    //window.open(`http://${viewerURL}/viewer?url=${urlRetrieve}`, '_blank');
  }

  /**
   * Call backend to import study/serie to local pacs
   * */
  ImportStow() {
    const drimboxSource = "localhost:8082";
    if (!this.openImportSerie) {
      this.http.get(`/api/stow/${drimboxSource}?studyUID=${this.studyInstanceLocal}`).subscribe(data => console.log("data"));
    }
    else {
      this.http.get(`/api/stow/${drimboxSource}?studyUID=${this.studyInstanceLocal}&serieUID=${this.serieInstanceLocal}`).subscribe(data => console.log("data"));
    }
  }

  /**
   * Display import panel from study level
   * @param doc from the study
   */
  openFormStudy(doc): void {
    // Retrieve clicked studyInstanceId
    this.studyInstanceLocal = doc.retrieveURL;
    this.disp = true;
    this.openImportSerie = false;
  }

  /**
   * Display import panel from serie level
   * @param serie from the serie
   */
  openFormSerie(serie): void {
    // Retrieve clicked studyInstanceId and serieInstanceId
    this.studyInstanceLocal = serie.retrieveURL.split("studies/")[1].split("/")[0];
    this.serieInstanceLocal = serie.retrieveURL.split("series/")[1].split("/")[0];
    this.disp = true;
    this.openImportSerie = true;
  }

  /**
   * Close import panel
   * */
  closeForm(): void {
    this.disp = false;
  }

  /**
   * Open pdf page from cda
   * @param doc
   */
  openPdf(doc) {
    // Check if already retrieve the pdf
    if (doc.rapports.length !== 0) {
      const pdfWindow = window.open("");
      pdfWindow.document.write(`<embed width='100%' height='100%' src='data:application/pdf;base64,${encodeURI(doc.rapports[0].pdfValue)}'></embed>`);
    }
    // Try retrieve the pdf if exists
    else {
      this.td3_2(doc, "study");
    }
  }
}

