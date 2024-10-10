/******************************************************************************
 * Spine Runtimes License Agreement
 * Last updated July 28, 2023. Replaces all prior versions.
 *
 * Copyright (c) 2013-2023, Esoteric Software LLC
 *
 * Integration of the Spine Runtimes into software or otherwise creating
 * derivative works of the Spine Runtimes is permitted under the terms and
 * conditions of Section 2 of the Spine Editor License Agreement:
 * http://esotericsoftware.com/spine-editor-license
 *
 * Otherwise, it is permitted to integrate the Spine Runtimes into software or
 * otherwise create derivative works of the Spine Runtimes (collectively,
 * "Products"), provided that each user of the Products must obtain their own
 * Spine Editor license and redistribution of the Products in any form must
 * include this license and copyright notice.
 *
 * THE SPINE RUNTIMES ARE PROVIDED BY ESOTERIC SOFTWARE LLC "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ESOTERIC SOFTWARE LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES,
 * BUSINESS INTERRUPTION, OR LOSS OF USE, DATA, OR PROFITS) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THE
 * SPINE RUNTIMES, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *****************************************************************************/

package com.esotericsoftware.spine.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Null;
import com.esotericsoftware.spine.android.utils.HttpUtils;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.BitmapFactory;
import android.os.Build;

/** Atlas data loaded from a `.atlas` file and its corresponding `.png` files. For each atlas image, a corresponding
 * {@link Bitmap} and {@link Paint} is constructed, which are used when rendering a skeleton that uses this atlas.
 *
 * Use the static methods {@link AndroidTextureAtlas#fromAsset(String, Context)}, {@link AndroidTextureAtlas#fromFile(File)}, and
 * {@link AndroidTextureAtlas#fromHttp(URL, File)} to load an atlas. */
public class AndroidTextureAtlas {
	private interface BitmapLoader {
		Bitmap load (String path);
	}

	private final Array<AndroidTexture> textures = new Array<>();
	private final Array<AtlasRegion> regions = new Array<>();

	private AndroidTextureAtlas (TextureAtlasData data, BitmapLoader bitmapLoader) {
		for (TextureAtlasData.Page page : data.getPages()) {
			page.texture = new AndroidTexture(bitmapLoader.load(page.textureFile.path()));
			textures.add((AndroidTexture)page.texture);
		}

		for (TextureAtlasData.Region region : data.getRegions()) {
			AtlasRegion atlasRegion = new AtlasRegion(region.page.texture, region.left, region.top, //
				region.rotate ? region.height : region.width, //
				region.rotate ? region.width : region.height);
			atlasRegion.index = region.index;
			atlasRegion.name = region.name;
			atlasRegion.offsetX = region.offsetX;
			atlasRegion.offsetY = region.offsetY;
			atlasRegion.originalHeight = region.originalHeight;
			atlasRegion.originalWidth = region.originalWidth;
			atlasRegion.rotate = region.rotate;
			atlasRegion.degrees = region.degrees;
			atlasRegion.names = region.names;
			atlasRegion.values = region.values;
			if (region.flip) atlasRegion.flip(false, true);
			regions.add(atlasRegion);
		}
	}

	/** Returns the first region found with the specified name. This method uses string comparison to find the region, so the
	 * result should be cached rather than calling this method multiple times. */
	public @Null AtlasRegion findRegion (String name) {
		for (int i = 0, n = regions.size; i < n; i++)
			if (regions.get(i).name.equals(name)) return regions.get(i);
		return null;
	}

	public Array<AndroidTexture> getTextures () {
		return textures;
	}

	public Array<AtlasRegion> getRegions () {
		return regions;
	}

	/** Loads an {@link AndroidTextureAtlas} from the file {@code atlasFileName} from assets using {@link Context}.
	 *
	 * Throws a {@link RuntimeException} in case the atlas could not be loaded. */
	public static AndroidTextureAtlas fromAsset (String atlasFileName, Context context) {
		TextureAtlasData data = new TextureAtlasData();
		AssetManager assetManager = context.getAssets();

		try {
			FileHandle inputFile = new FileHandle() {
				@Override
				public InputStream read () {
					try {
						return assetManager.open(atlasFileName);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			};
			data.load(inputFile, new FileHandle(atlasFileName).parent(), false);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}

		return new AndroidTextureAtlas(data, path -> {
			path = path.startsWith("/") ? path.substring(1) : path;
			try (InputStream in = new BufferedInputStream(assetManager.open(path))) {
				return BitmapFactory.decodeStream(in);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		});
	}

	/** Loads an {@link AndroidTextureAtlas} from the file {@code atlasFileName}.
	 *
	 * Throws a {@link RuntimeException} in case the atlas could not be loaded. */
	public static AndroidTextureAtlas fromFile (File atlasFile) {
		TextureAtlasData data;
		try {
			data = loadTextureAtlasData(atlasFile);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new AndroidTextureAtlas(data, path -> {
			File imageFile = new File(path);
			try (InputStream in = new BufferedInputStream(inputStream(imageFile))) {
				return BitmapFactory.decodeStream(in);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		});
	}

	/** Loads an {@link AndroidTextureAtlas} from the URL {@code atlasURL}.
	 *
	 * Throws a {@link Exception} in case the atlas could not be loaded. */
	public static AndroidTextureAtlas fromHttp (URL atlasUrl, File targetDirectory) {
		File atlasFile = HttpUtils.downloadFrom(atlasUrl, targetDirectory);
		TextureAtlasData data;
		try {
			data = loadTextureAtlasData(atlasFile);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new AndroidTextureAtlas(data, path -> {
			String fileName = path.substring(path.lastIndexOf('/') + 1);

			String atlasUrlPath = atlasUrl.getPath();
			int lastSlashIndex = atlasUrlPath.lastIndexOf('/');
			String imagePath = atlasUrlPath.substring(0, lastSlashIndex + 1) + fileName;

			File imageFile;
			try {
				URL imageUrl = new URL(atlasUrl.getProtocol(), atlasUrl.getHost(), atlasUrl.getPort(), imagePath);
				imageFile = HttpUtils.downloadFrom(imageUrl, targetDirectory);
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}

			try (InputStream in = new BufferedInputStream(inputStream(imageFile))) {
				return BitmapFactory.decodeStream(in);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		});
	}

	private static InputStream inputStream (File file) throws Exception {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			return Files.newInputStream(file.toPath());
		} else {
			// noinspection IOStreamConstructor
			return new FileInputStream(file);
		}
	}

	private static TextureAtlasData loadTextureAtlasData (File atlasFile) {
		TextureAtlasData data = new TextureAtlasData();
		FileHandle inputFile = new FileHandle() {
			@Override
			public InputStream read () {
				try {
					return new FileInputStream(atlasFile);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		};
		data.load(inputFile, new FileHandle(atlasFile).parent(), false);
		return data;
	}
}
