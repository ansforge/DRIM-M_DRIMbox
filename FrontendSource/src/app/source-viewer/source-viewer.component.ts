import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CookieService } from 'ngx-cookie-service';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-source-viewer',
  templateUrl: './source-viewer.component.html',
  styleUrls: ['./source-viewer.component.css']
})
export class SourceViewerComponent implements OnInit {

  display = false;
  url = "";
  studyUID = "";
  isChecked = false;
  connected = false;

  // displaying panel to chose activity struct
  structurePanel = false;
  // tab with all activity struct from the user connected
  secteurActs: string[] = [];
  // struct chosen by user
  selectedStruct = '';

  /**
   * Constructor : manage local datas, session token and check if user connected
   * @param http for rest requests
   * @param cookieService to manage user cookies
   * @param route to manage angular routes
   */
  constructor(private readonly http: HttpClient, private readonly cookieService: CookieService, private readonly route: ActivatedRoute) {
    this.route.queryParams.subscribe(params => {
      if (params['requestType'] !== undefined && params['studyUID'] !== undefined && params['accessionNumber'] !== undefined && params['idCDA'] !== undefined) {
        console.log("valid parameters")
        this.studyUID = params['studyUID'];
        this.cookieService.set("requestType", params['requestType']);
        this.cookieService.set("studyUID", this.studyUID);
        this.cookieService.set("accessionNumber", params['accessionNumber']);
        this.cookieService.set("idCDA", params['idCDA']);
      }
      // Generate sessionToken
      if (!cookieService.check("SessionToken")) {
        const uuid = this.generateUUID();
        this.cookieService.set("SessionToken", uuid);
      }
      this.verifyConnection();
    });
  }
  ngOnInit(): void {
  }

  /**
   * Verify with backend if user connected
   * */
  verifyConnection() {
    console.log("verifyConnection");
    this.http.get('/api-source/auth', { responseType: 'text' }).subscribe(data => {
      // Back can answer : connected -- means user is already connected
      //                   connected but no structure : + list structs -- means user is already connected but activity struct no selected, gives list of user structs
      //                   no connected : + url -- means user is not connected, gives url to ProSanteConnect
      if (data.startsWith("no connected")) {
        // No connected, redirect to ProSanteconnect window
        this.url = data.split(": ")[1];
        this.connected = false;
        this.display = true;
      }

      else {
        this.connected = true;

        if (localStorage.getItem('Remember') === "false" && this.studyUID !== "") {
          this.display = true;
        }
        else {
          this.http.get('/api-vi1-source/check' + "?studyInstanceUID=" + this.cookieService.get("studyUID") + "&idCDA=" + this.cookieService.get("idCDA") + "&accessionNumber=" +
            this.cookieService.get("accessionNumber") + "&requestType=" + this.cookieService.get("requestType"), { observe: 'response' }).subscribe(data => {
              console.log(data.body);
              console.log(data.status);
              if (data.status === 200) {
                window.location.replace("/viewer/dicomjson?url=" + environment.hostsource + "/api-vi1-source/metadata/" + this.cookieService.get("studyUID"));
              }
              else
                console.log(data.body);
            });
        }
      }
    });
  }

  /**
   * Redirect to PSC or OHIF if user is already connected
   * */
  redirectOnClick() {
    localStorage.setItem('Remember', String(this.isChecked));

    if (this.connected) {
      // To OHIF
      this.http.get('/api-vi1-source/check' + "?studyInstanceUID=" + this.cookieService.get("studyUID") + "&idCDA=" + this.cookieService.get("idCDA") + "&accessionNumber=" +
        this.cookieService.get("accessionNumber") + "&requestType=" + this.cookieService.get("requestType"), { observe: 'response' }).subscribe(data => {
          console.log(data.body);
          console.log(data.status);
          if (data.status === 200) {
            window.location.replace("/viewer/dicomjson?url=" + environment.hostsource + "/api-vi1-source/metadata/" + this.cookieService.get("studyUID"));
          }
          else
            console.log(data.body);
        });

    }
    else {
      // To PSC
      window.location.replace(this.url);
    }
  }

  /**
* Get to back to retrieve list of user structures
* */
  askStructure() {
    // Display structure panel
    this.structurePanel = true;

    this.http.get('/api-source/location', { responseType: 'text' }).subscribe(data => {
      // Retrieve structure list
      this.secteurActs = data.split("/");
    });
  }

  /**
   * Send to back structure chosen by user
   * */
  sendSect() {
    // verify a structure is selected
    if (this.selectedStruct !== undefined) {
      this.http.post('/api-source/location', this.selectedStruct, {
        responseType: 'text',
        observe: 'response'
      }).subscribe(
        response => {
          if (response.body!.startsWith("Success")) {

            // Hide struct panel
            this.structurePanel = false;
            this.verifyConnection();
          }
        },
        error => {
          console.log("Error", error, this.selectedStruct);
        },
        () => {
          console.log("POST is completed");
        });
    }
  }

  /**
   * Function to generate UUID
   * */
  generateUUID() { // Public Domain/MIT
    let d = new Date().getTime();//Timestamp
    let d2 = ((typeof performance !== 'undefined') && performance.now && (performance.now() * 1000)) || 0;//Time in microseconds since page-load or 0 if unsupported
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      let r = Math.random() * 16;//random number between 0 and 16
      if (d > 0) {//Use timestamp until depleted
        r = (d + r) % 16 | 0;
        d = Math.floor(d / 16);
      } else {//Use microseconds since page-load if supported
        r = (d2 + r) % 16 | 0;
        d2 = Math.floor(d2 / 16);
      }
      return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
  }
}
