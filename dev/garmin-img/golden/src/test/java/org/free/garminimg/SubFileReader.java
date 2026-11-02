/*
 * Part of libGarminImg, the Asamm fork of JGarminImgParser (Copyright (C) 2006 Patrick
 * Valsecchi); kept as the reference parser of the dump comparison.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.free.garminimg;

public abstract class SubFileReader {

	public abstract ImgSubFile parse(String filenam, String filetype,
			int fileSize, int blocSize, int offset, ImgFileBag imgFileBag);

}
