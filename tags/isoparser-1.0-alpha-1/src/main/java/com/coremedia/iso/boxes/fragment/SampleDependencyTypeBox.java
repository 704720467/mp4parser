/*
 * Copyright 2009 castLabs GmbH, Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coremedia.iso.boxes.fragment;

import com.coremedia.iso.BoxFactory;
import com.coremedia.iso.IsoFile;
import com.coremedia.iso.IsoInputStream;
import com.coremedia.iso.IsoOutputStream;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.BoxContainer;
import com.coremedia.iso.boxes.FullBox;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.SampleSizeBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * aligned(8) class SampleDependencyTypeBox
 * extends FullBox(�sdtp�, version = 0, 0) {
 * for (i=0; i < sample_count; i++){
 * unsigned int(2) reserved = 0;
 * unsigned int(2) sample_depends_on;
 * unsigned int(2) sample_is_depended_on;
 * unsigned int(2) sample_has_redundancy;
 * }
 * }
 */
public class SampleDependencyTypeBox extends FullBox {
  public static final String TYPE = "sdtp";

  private List<Entry> sampleEntries = new ArrayList<Entry>();

  public static class Entry {
    private int reserved;
    private int sampleDependsOn;
    private int sampleIsDependentOn;
    private int sampleHasRedundancy;

    public int getReserved() {
      return reserved;
    }

    public int getSampleDependsOn() {
      return sampleDependsOn;
    }

    public int getSampleIsDependentOn() {
      return sampleIsDependentOn;
    }

    public int getSampleHasRedundancy() {
      return sampleHasRedundancy;
    }

    @Override
    public String toString() {
      return "Entry{" +
              "reserved=" + reserved +
              ", sampleDependsOn=" + sampleDependsOn +
              ", sampleIsDependentOn=" + sampleIsDependentOn +
              ", sampleHasRedundancy=" + sampleHasRedundancy +
              '}';
    }
  }

  public SampleDependencyTypeBox() {
    super(IsoFile.fourCCtoBytes(TYPE));
  }

  public String getDisplayName() {
    return "Independent and Disposable Samples Box";
  }

  protected long getContentSize() {
    return getSampleCount();
  }

  protected void getContent(IsoOutputStream os) throws IOException {
    for (Entry entry : sampleEntries) {
      int temp = entry.reserved << 6;

      temp = temp | (entry.sampleDependsOn << 4);
      temp = temp | (entry.sampleIsDependentOn << 2);
      temp = temp | entry.sampleHasRedundancy;

      os.write(temp);
    }
  }

  @Override
  public void parse(IsoInputStream in, long size, BoxFactory boxFactory, Box lastMovieFragmentBox) throws IOException {
    super.parse(in, size, boxFactory, lastMovieFragmentBox);
    long sampleCount = getSampleCount();

    for (int i = 0; i < sampleCount; i++) {
      Entry entry = new Entry();
      int temp = in.readUInt8();

      entry.reserved = (temp & 0xC0) >> 6;
      entry.sampleDependsOn = (temp & 0x30) >> 4;
      entry.sampleIsDependentOn = (temp & 0xC) >> 2;
      entry.sampleHasRedundancy = temp & 0x3;

      sampleEntries.add(entry);
    }

  }

  private long getSampleCount() {
    BoxContainer traf = this.getParent();
    TrackRunBox[] trackRunBoxes = traf.getBoxes(TrackRunBox.class);
    if (trackRunBoxes.length > 1) {
      for (TrackRunBox trackRunBox : trackRunBoxes) {
        System.out.println("Found (additional) Track Run Box: " + trackRunBox + " in " + traf);
      }
      throw new RuntimeException("More than one Track Fragment Header Box in Track Fragment Box.");
    } else if (trackRunBoxes.length == 1) {
      return trackRunBoxes[0].getSampleCount();
    }

    System.out.println("Couldn't find Track Run Box. Trying to determine sample count by looking up Sample Size Boxes");
    BoxContainer bc = this.getParent();
    while (bc.getParent() != null) {
      bc = bc.getParent();
    }
    MovieBox[] movieBoxes = bc.getBoxes(MovieBox.class);
    if (movieBoxes.length == 0) {
      System.out.println("No Movie Box found in " + bc);
      return 0;
    }
    MovieBox movieBox = movieBoxes[0];

    SampleSizeBox[] sampleSizeBoxes = movieBox.getBoxes(SampleSizeBox.class);

    long sampleCount = 0;
    if (sampleSizeBoxes.length > 1) {
      System.out.println("Found more than one Sample Size Box in movie box. Taking first.");
      for (SampleSizeBox sampleSizeBox : sampleSizeBoxes) {
        System.out.println("found Sample Size Box " + sampleSizeBox + " in " + movieBox);
      }
    } else if (sampleSizeBoxes.length > 0) {
      sampleCount = sampleSizeBoxes[0].getSampleCount();
    } else {
      System.out.println("No Sample Size Box found in " + movieBox.getDisplayName());
    }
    return sampleCount;
  }

  public List<Entry> getSampleEntries() {
    return sampleEntries;
  }

  public int getSampleEntriesCount() {
    return sampleEntries.size();
  }

  public String getSampleEntriesAsString() {
    StringBuilder stringBuilder = new StringBuilder();
    for (Entry sampleEntry : sampleEntries) {
      stringBuilder.append(sampleEntry);
    }
    return stringBuilder.toString();
  }
}
