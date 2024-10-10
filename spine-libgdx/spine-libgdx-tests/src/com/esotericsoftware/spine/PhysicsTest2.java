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

package com.esotericsoftware.spine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import com.esotericsoftware.spine.Skeleton.Physics;
import com.esotericsoftware.spine.utils.TwoColorPolygonBatch;

/** Demonstrates loading, animating, and rendering a skeleton.
 * @see SkeletonAssetManagerTest */
public class PhysicsTest2 extends ApplicationAdapter {
	OrthographicCamera camera;
	TwoColorPolygonBatch batch;
	SkeletonRenderer renderer;
	SkeletonRendererDebug debugRenderer;

	TextureAtlas atlas;
	Skeleton skeleton;
	AnimationState state;

	float lastX = 0;
	float lastY = 0;

	public void create () {
		camera = new OrthographicCamera();
		batch = new TwoColorPolygonBatch();
		renderer = new SkeletonRenderer();
		renderer.setPremultipliedAlpha(true); // PMA results in correct blending without outlines.
		debugRenderer = new SkeletonRendererDebug();
		debugRenderer.setBoundingBoxes(false);
		debugRenderer.setRegionAttachments(false);

		atlas = new TextureAtlas(Gdx.files.internal("celestial-circus/celestial-circus-pma.atlas"));
		SkeletonBinary binary = new SkeletonBinary(atlas); // This loads skeleton JSON data, which is stateless.
		binary.setScale(0.1f); // Load the skeleton at 60% the size it was in Spine.
		SkeletonData skeletonData = binary.readSkeletonData(Gdx.files.internal("celestial-circus/celestial-circus-pro.skel"));

		skeleton = new Skeleton(skeletonData); // Skeleton holds skeleton state (bone positions, slot attachments, etc).
		skeleton.setPosition(320, 100);

		AnimationStateData stateData = new AnimationStateData(skeletonData); // Defines mixing (crossfading) between animations.
		state = new AnimationState(stateData); // Holds the animation state for a skeleton (current animation, time, etc).

		Gdx.input.setInputProcessor(new InputAdapter() {
			public boolean touchDown (int screenX, int screenY, int pointer, int button) {
				lastX = screenX;
				lastY = screenY;
				return true;
			}

			public boolean touchDragged (int screenX, int screenY, int pointer) {
				skeleton.x += screenX - lastX;
				skeleton.y += lastY - screenY;
				lastX = screenX;
				lastY = screenY;
				return true;
			}
		});
	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		state.update(delta); // Update the animation time.

		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		state.apply(skeleton); // Poses skeleton using current animations. This sets the bones' local SRT.
		skeleton.update(delta);
		skeleton.updateWorldTransform(Physics.update); // Uses the bones' local SRT to compute their world SRT.

		// Configure the camera, SpriteBatch, and SkeletonRendererDebug.
		camera.update();
		batch.getProjectionMatrix().set(camera.combined);
		debugRenderer.getShapeRenderer().setProjectionMatrix(camera.combined);

		batch.begin();
		renderer.draw(batch, skeleton); // Draw the skeleton images.
		batch.end();

		// debugRenderer.draw(skeleton); // Draw debug lines.
	}

	public void resize (int width, int height) {
		camera.setToOrtho(false); // Update camera with new size.
	}

	public void dispose () {
		atlas.dispose();
	}

	public static void main (String[] args) throws Exception {
		new Lwjgl3Application(new PhysicsTest2());
	}
}
