/*
 *  DatabaseManager.java - DRIMBox
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

package com.bcom.drimbox.dmp.database;

import com.bcom.drimbox.dmp.xades.file.CDAFile;
import com.bcom.drimbox.dmp.xades.file.KOSFile;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class DatabaseManager {

    /***
     * Add an entity to the database
     * @param cdaFile CDA file to store
     * @param kosFile KOS file to store
     * @return true if success, false otherwise (e.g. studyUID already exists)
     */
    @Transactional
    public Boolean addEntity(CDAFile cdaFile, KOSFile kosFile,  byte[] rawMetadata, byte[] signDOC) {
        try {
            SourceEntity s = new SourceEntity();
            s.studyUID = cdaFile.getStudyID();
            s.rawCDA = cdaFile.getRawData();
            s.rawKOS = kosFile.getRawData();
            s.cdaID = cdaFile.getCdaID();
            s.ipp = cdaFile.getOruIpp();
            s.rawMetadata = rawMetadata;
            s.signDOC = signDOC;
            s.persistAndFlush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
    
    /***
     * Add an entity to the database
     * @param cdaFile CDA file to store
     * @param kosFile KOS file to store
     * @return true if success, false otherwise (e.g. studyUID already exists)
     */
    @Transactional
    public Boolean addEntity( byte[] kosFile,  byte[] rawMetadata, byte[] signDoc, String studyInstanceUID, String cdaID, String ipp) {
        try {
            SourceEntity s = new SourceEntity();
            s.studyUID = studyInstanceUID;
            s.rawCDA = null;
            s.rawKOS = kosFile;
            s.cdaID = cdaID;
            s.ipp = ipp;
            s.rawMetadata = rawMetadata;
            s.signDOC = signDoc;
            s.persistAndFlush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Get KOS and CDA associated with studyUID
     * @param studyUID Study UID
     * @return Null if not found or assicoated exams
     */
    public SourceEntity getEntity(String studyUID) {
        return SourceEntity.findById(studyUID);
    }
    
    public PanacheQuery<SourceEntity> getEntities() {
    	return SourceEntity.findAll();
    }
}
