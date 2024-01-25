/*
 *  FindEntryUUIDDocumentRequest.java - DRIMBox
 *
 * N°IDDN : IDDN.FR.001.020012.000.S.C.2023.000.30000
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

package com.bcom.drimbox.dmp.request;

public class FindEntryUUIDDocumentRequest extends BaseRequest{

	@Override
	protected String actionName() {
		return "urn:ihe:iti:2007:RegistryStoredQuery";
	}

	@Override
	protected String serviceName() {
		return "registry";
	}

	public FindEntryUUIDDocumentRequest(String uniqueID) {
		super();

		var pAdhocQueryRequest = soapRequest.createElement("ns3:AdhocQueryRequest");
		pAdhocQueryRequest.setAttribute("xmlns", "urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0");
		pAdhocQueryRequest.setAttribute("xmlns:ns2", "urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0");
		pAdhocQueryRequest.setAttribute("xmlns:ns3", "urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0");
		pAdhocQueryRequest.setAttribute("xmlns:ns4", "urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0");
		body.appendChild(pAdhocQueryRequest);

		var pResponseOption = soapRequest.createElement("ns3:ResponseOption");
		pResponseOption.setAttribute("returnComposedObjects", "true");
		pResponseOption.setAttribute("returnType", "ObjectRef");
		pAdhocQueryRequest.appendChild(pResponseOption);

		var pAdhocQuery = soapRequest.createElement("AdhocQuery");
		pAdhocQuery.setAttribute("id", "urn:uuid:5c4f972b-d56b-40ac-a5fc-c8ca9b40b9d4");
		pAdhocQueryRequest.appendChild(pAdhocQuery);

		createSlot(pAdhocQuery,"$XDSDocumentEntryUniqueId", "('" + uniqueID + "')");

	}
}
