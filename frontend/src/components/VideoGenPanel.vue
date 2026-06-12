<template>
  <div class="video-gen-panel">
    <div class="vgp-header">
      <div class="vgp-title">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="23 7 16 12 23 17 23 7"/>
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
        </svg>
        <span>视频生成 (即梦3.0 Pro)</span>
      </div>
      <button class="btn-icon-sm" title="返回聊天" @click="$emit('close')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>

    <!-- Mode tabs -->
    <div class="vgp-mode-tabs">
      <button class="mode-tab" :class="{ active: mode === 'single' }" @click="mode = 'single'">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="23 7 16 12 23 17 23 7"/>
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
        </svg>
        单镜头生成
      </button>
      <button class="mode-tab" :class="{ active: mode === 'storyboard' }" @click="mode = 'storyboard'">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
          <rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>
        </svg>
        分镜编排
      </button>
    </div>

    <div class="vgp-body">
      <!-- Existing single-shot mode -->
      <template v-if="mode === 'single'">
      <!-- Task list -->
      <div v-if="tasks.length > 0" class="vgp-task-list">
        <div class="vgp-section-title task-list-header" @click="tasksCollapsed = !tasksCollapsed">
          <span>我的任务 ({{ tasks.length }})</span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            class="collapse-chevron" :class="{ collapsed: tasksCollapsed }">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </div>
        <template v-if="!tasksCollapsed">
          <div
            v-for="task in tasks"
            :key="task.id"
            class="vgp-task-item"
          >
          <div class="task-status-icon">
            <template v-if="isPending(task.status)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
              </svg>
            </template>
            <template v-else-if="task.status === 'SUCCEEDED'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </template>
            <template v-else-if="task.status === 'FAILED'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--danger)" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
            </template>
          </div>
          <div class="task-info">
            <div class="task-prompt">{{ task.prompt.substring(0, 50) }}{{ task.prompt.length > 50 ? '...' : '' }}</div>
            <div class="task-meta">
              <span :class="'task-status-text status-' + task.status.toLowerCase()">{{ statusLabel(task.status) }}</span>
              <span v-if="task.errorMessage" class="task-error">{{ task.errorMessage }}</span>
            </div>
          </div>
          <div class="task-actions">
            <button
              v-if="task.status === 'SUCCEEDED'"
              class="btn-play"
              @click="$emit('play', task)"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                <polygon points="8 5 19 12 8 19 8 5"/>
              </svg>
              播放
            </button>
            <button
              v-if="task.status === 'SUCCEEDED'"
              class="btn-post-process"
              :disabled="processingTaskId === task.id"
              title="嵌入字幕"
              @click="postProcessTask(task, 'subtitle')"
            >
              {{ processingTaskId === task.id && processingType === 'subtitle' ? '处理中...' : '字幕' }}
            </button>
            <button
              v-if="task.status === 'SUCCEEDED'"
              class="btn-post-process"
              :disabled="processingTaskId === task.id"
              title="TTS配音"
              @click="postProcessTask(task, 'narrate')"
            >
              {{ processingTaskId === task.id && processingType === 'narrate' ? '处理中...' : '配音' }}
            </button>
            <button
              class="btn-delete-task"
              title="删除"
              @click="$emit('deleteTask', task.id)"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </button>
          </div>
        </div>
        </template>
      </div>

      <!-- Submit form -->
      <div class="vgp-form">
        <div class="vgp-section-title">新建视频</div>

        <div class="form-group">
          <label class="form-label">提示词</label>
          <textarea
            v-model="prompt"
            class="form-textarea"
            rows="4"
            placeholder="描述你想要的视频内容，支持中英文..."
          ></textarea>
        </div>

        <!-- Prompt toolbar -->
        <div class="prompt-toolbar">
          <div class="toolbar-left">
            <button class="btn-tool" :class="{ active: showTemplates }" title="模板" @click="toggleTemplates">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/>
              </svg>
              模板
            </button>
            <button class="btn-tool" :disabled="enhancing || !prompt.trim()" title="AI增强" @click="enhancePrompt">
              <svg v-if="!enhancing" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
              </svg>
              {{ enhancing ? '增强中...' : '增强' }}
            </button>
            <div class="translate-wrapper">
              <button class="btn-tool" :disabled="translating || !prompt.trim()" title="翻译" @click="toggleTranslateMenu">
                <svg v-if="!translating" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 8l6 6"/><path d="M4 14l6-6 2-3"/><path d="M2 5h12"/><path d="M7 2h1"/><path d="M22 22l-5-10-5 10"/><path d="M14 18h6"/>
                </svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                  <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
                </svg>
                翻译
              </button>
              <div v-if="showTranslateMenu" class="translate-menu">
                <button @click="doTranslate('zh')">译为中文</button>
                <button @click="doTranslate('en')">译为英文</button>
                <button @click="doTranslate('auto')">统一润色</button>
              </div>
            </div>
          </div>
          <span class="char-count">{{ prompt.length }}/500</span>
        </div>

        <!-- Template selector -->
        <div v-if="showTemplates" class="template-selector">
          <div class="template-chips">
            <button v-for="t in templates" :key="t.id" class="chip-template" :class="{ preset: t.isPreset }" @click="selectTemplate(t)">{{ t.name }}</button>
            <div class="template-add-row">
              <input v-model="newTemplateName" class="input-new-template" placeholder="新模板名称" />
              <button class="btn-save-template" :disabled="!newTemplateName.trim() || !prompt.trim()" @click="saveCurrentAsTemplate">保存当前</button>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">首帧图片 (可选)</label>
          <div v-if="firstFramePreview" class="first-frame-preview">
            <img :src="firstFramePreview" alt="First frame" />
            <button class="btn-remove-img" @click="removeFirstFrame">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <button v-else class="btn-upload-img" @click="triggerFileInput">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21 15 16 10 5 21"/>
            </svg>
            上传图片
          </button>
          <input ref="fileInput" type="file" accept="image/*" class="file-hidden" @change="onFileChange" />
        </div>

        <div class="form-group">
          <button class="btn-toggle-advanced" @click="showAdvanced = !showAdvanced">
            高级设置
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              :class="{ rotated: showAdvanced }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
        </div>

        <div v-if="showAdvanced" class="advanced-settings">
          <div class="form-row">
            <label class="form-label">时长</label>
            <div class="duration-input-row">
              <button
                :class="{ active: duration === 5 }"
                class="btn-option"
                @click="duration = 5"
              >5秒</button>
              <button
                :class="{ active: duration === 10 }"
                class="btn-option"
                @click="duration = 10"
              >10秒</button>
              <button
                :class="{ active: duration === 12 }"
                class="btn-option"
                @click="duration = 12"
              >12秒</button>
              <div class="duration-custom">
                <input
                  type="number"
                  class="duration-input"
                  :value="duration"
                  min="4"
                  max="12"
                  step="1"
                  @input="onDurationInput"
                  @focus="onDurationFocus"
                />
                <span class="duration-unit">秒</span>
              </div>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">比例</label>
            <div class="btn-group">
              <button
                v-for="r in ratios"
                :key="r"
                :class="{ active: aspectRatio === r }"
                class="btn-option"
                @click="aspectRatio = r"
              >{{ r }}</button>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">生成音频</label>
            <label class="toggle-switch">
              <input type="checkbox" v-model="generateAudio" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="negative-prompt-section">
            <label class="form-label">排除内容（反向提示词）</label>
            <div class="negative-tags">
              <button v-for="tag in negativeTagOptions" :key="tag" class="tag-negative" :class="{ selected: selectedNegativeTags.includes(tag) }" @click="toggleNegativeTag(tag)">{{ tag }}</button>
            </div>
            <input type="text" v-model="customNegative" class="input-custom-negative" placeholder="输入想排除的内容，逗号分隔..." />
          </div>
        </div>

        <button
          class="btn-submit"
          :disabled="!prompt.trim() || submitting"
          @click="submitTask"
        >
          <svg v-if="submitting" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
            <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="23 7 16 12 23 17 23 7"/>
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
          </svg>
          {{ submitting ? '提交中...' : '开始生成' }}
        </button>
      </div>
      </template>

      <!-- Storyboard mode -->
      <template v-if="mode === 'storyboard'">
        <!-- Phase 1: Input idea -->
        <div class="storyboard-section">
          <div class="vgp-section-title">视频创意</div>
          <textarea
            v-model="storyboardIdea"
            class="form-textarea"
            rows="3"
            placeholder="描述你的视频创意，AI 将自动拆分为分镜脚本..."
          ></textarea>
          <div class="sb-idea-row">
            <div class="sb-shot-count">
              <label>镜头数量:</label>
              <input type="number" v-model.number="shotCount" min="2" max="12" class="shot-count-input" />
              <span>个</span>
            </div>
            <button
              class="btn-generate-sb"
              :disabled="generating || !storyboardIdea.trim()"
              @click="generateStoryboard"
            >
              <svg v-if="generating" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
              </svg>
              AI 生成分镜
            </button>
          </div>
        </div>

        <!-- Phase 2: Edit storyboard / Read-only summary -->
        <div v-if="shots.length > 0 && resultDisplayMode !== 'merged'" class="storyboard-section">
          <div class="sb-title-row">
            <input v-model="storyboardTitle" class="sb-title-input" placeholder="分镜标题..." />
            <button class="btn-save-sb" :disabled="savingSb || !storyboardTitle.trim()" @click="saveStoryboard">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/>
                <polyline points="17 21 17 13 7 13 7 21"/>
                <polyline points="7 3 7 8 15 8"/>
              </svg>
              保存分镜
            </button>
          </div>

          <div class="shot-table">
            <div class="shot-table-header">
              <span class="col-num">镜头</span>
              <span class="col-desc">画面描述</span>
              <span class="col-cam">运镜</span>
              <span class="col-dur">时长</span>
              <span class="col-actions">操作</span>
            </div>
            <div v-for="(shot, i) in shots" :key="i" class="shot-row">
              <span class="col-num">{{ i + 1 }}</span>
              <textarea v-model="shot.shotDescription" class="shot-desc-input" rows="2"></textarea>
              <select v-model="shot.cameraMovement" class="shot-cam-select">
                <option value="固定">固定</option>
                <option value="推镜">推镜</option>
                <option value="拉镜">拉镜</option>
                <option value="摇镜">摇镜</option>
                <option value="跟镜">跟镜</option>
                <option value="升降">升降</option>
              </select>
              <input type="number" v-model.number="shot.duration" class="shot-dur-input" min="4" max="12" />
              <div class="col-actions">
                <button class="btn-shot-action" title="上移" :disabled="i === 0" @click="moveShot(i, -1)">↑</button>
                <button class="btn-shot-action" title="下移" :disabled="i === shots.length - 1" @click="moveShot(i, 1)">↓</button>
                <button class="btn-shot-action btn-shot-delete" title="删除" @click="removeShot(i)">✕</button>
              </div>
              <button class="btn-shot-expand" @click="showShotDetail = showShotDetail === i ? null : i" title="展开详情">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                  :class="{ rotated: showShotDetail === i }">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </button>
            </div>
            <div v-if="showShotDetail === i" class="shot-detail-row">
              <div class="shot-detail-fields">
                <label>场景备注:</label>
                <input type="text" v-model="shot.sceneNote" placeholder="场景标识（5-10字）" class="shot-detail-input" />
                <label>音效提示:</label>
                <input type="text" v-model="shot.audioHint" placeholder="音效/配乐建议" class="shot-detail-input" />
              </div>
            </div>
          </div>

          <button class="btn-add-shot" @click="addShot">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            添加镜头
          </button>
        </div>

        <!-- Merged mode: read-only shot summary -->
        <div v-if="shots.length > 0 && resultDisplayMode === 'merged'" class="storyboard-section shot-summary-section">
          <div class="vgp-section-title">镜头列表</div>
          <div class="shot-summary-list">
            <div v-for="(shot, i) in shots" :key="'summary-'+i" class="shot-summary-item">
              <span class="summary-num" :class="'summary-status-' + (shot.status || 'PENDING').toLowerCase()">{{ i + 1 }}</span>
              <div class="summary-content">
                <p class="summary-desc">{{ shot.shotDescription }}</p>
                <span class="summary-meta">{{ shot.cameraMovement || '固定' }} · {{ shot.duration || 5 }}s{{ shot.status === 'SUCCEEDED' ? ' · ✅ 已完成' : shot.status === 'FAILED' ? ' · ❌ 失败' : ' · ⏳ ' + (shot.status || 'PENDING') }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Phase 3: Submit settings (reuse existing advanced settings) -->
        <div v-if="shots.length > 0" class="storyboard-section">
          <div v-if="resultDisplayMode !== 'merged'" class="vgp-section-title">生成设置</div>

          <div v-if="resultDisplayMode !== 'merged'" class="form-row">
            <label class="form-label">比例</label>
            <div class="btn-group">
              <button v-for="r in ratios" :key="r" :class="{ active: aspectRatio === r }" class="btn-option" @click="aspectRatio = r">{{ r }}</button>
            </div>
          </div>

          <div v-if="resultDisplayMode !== 'merged'" class="form-row">
            <label class="form-label">生成音频</label>
            <label class="toggle-switch">
              <input type="checkbox" v-model="generateAudio" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div v-if="resultDisplayMode !== 'merged'" class="negative-prompt-section">
            <label class="form-label">排除内容（反向提示词）</label>
            <div class="negative-tags">
              <button v-for="tag in negativeTagOptions" :key="tag" class="tag-negative" :class="{ selected: selectedNegativeTags.includes(tag) }" @click="toggleNegativeTag(tag)">{{ tag }}</button>
            </div>
            <input type="text" v-model="customNegative" class="input-custom-negative" placeholder="输入想排除的内容，逗号分隔..." />
          </div>

          <button v-if="resultDisplayMode !== 'merged'" class="btn-submit" :disabled="!shots.length || submittingSb" @click="submitAllShots">
            <svg v-if="submittingSb" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
              <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
            </svg>
            🚀 全部生成 ({{ shots.length }}个镜头)
          </button>

          <!-- Display mode toggle -->
          <div class="sb-display-mode">
            <span class="display-label">展示模式</span>
            <div class="display-mode-cards">
              <div class="display-card"
                :class="{ active: resultDisplayMode === 'individual' }"
                @click="resultDisplayMode = 'individual'">
                <div class="display-card-icon">
                  <svg width="40" height="28" viewBox="0 0 40 28">
                    <rect x="1" y="2" width="11" height="24" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"/>
                    <polygon points="6,8 6,20 10,14" fill="currentColor" opacity="0.5"/>
                    <rect x="15" y="2" width="11" height="24" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"/>
                    <polygon points="20,8 20,20 24,14" fill="currentColor" opacity="0.5"/>
                    <rect x="29" y="2" width="11" height="24" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.4"/>
                    <polygon points="34,8 34,20 38,14" fill="currentColor" opacity="0.3"/>
                  </svg>
                </div>
                <span class="display-card-title">独立视频</span>
                <span class="display-card-desc">逐个播放每个镜头</span>
              </div>
              <div class="display-card"
                :class="{ active: resultDisplayMode === 'merged' }"
                @click="resultDisplayMode = 'merged'">
                <div class="display-card-icon">
                  <svg width="40" height="28" viewBox="0 0 40 28">
                    <rect x="1" y="2" width="12" height="24" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"/>
                    <polygon points="6,8 6,20 10,14" fill="currentColor" opacity="0.4"/>
                    <line x1="13" y1="14" x2="17" y2="14" stroke="currentColor" stroke-width="1" stroke-dasharray="2,2"/>
                    <polygon points="18,9 25,14 18,19" fill="currentColor"/>
                    <rect x="25" y="2" width="14" height="24" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
                    <polygon points="31,8 31,20 37,14" fill="currentColor"/>
                  </svg>
                </div>
                <span class="display-card-title">合并播放</span>
                <span class="display-card-desc">FFmpeg 转场合并为一条视频</span>
              </div>
            </div>
          </div>

          <div v-if="resultDisplayMode === 'merged' && shots.some(s => s.status === 'SUCCEEDED')" class="merged-section">
            <div v-if="!mergedVideoUrl && !merging">
              <button class="btn-merged-play" :disabled="!storyboardId" @click="mergeVideos">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="2" y="3" width="8" height="10" rx="1"/><rect x="14" y="3" width="8" height="10" rx="1"/>
                  <polygon points="6 16 6 21 8 21 8 17 10 17 10 21 12 21 12 16 6 16"/>
                  <polygon points="16 16 16 21 18 21 18 17 20 17 20 21 22 21 22 16 16 16"/>
                </svg>
                FFmpeg 转场合并 ({{ shots.filter(s => s.status === 'SUCCEEDED').length }}个镜头)
              </button>
              <p class="merged-hint" v-if="!storyboardId">请先保存分镜</p>
            </div>
            <div v-if="merging" class="merging-status">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
              </svg>
              <span>正在合并视频，请稍候...</span>
            </div>
            <video v-if="mergedVideoUrl" :src="mergedVideoUrl" controls autoplay class="merged-video-player"></video>
          </div>

          <!-- Shot status display -->
          <div v-if="resultDisplayMode === 'individual' && shots.some(s => s.status && s.status !== 'PENDING')" class="shot-status-list">
            <div v-for="(shot, i) in shots" :key="'status-'+i" class="shot-status-row">
              <span>镜头{{ i + 1 }}</span>
              <span :class="'shot-status-badge status-' + (shot.status || 'PENDING').toLowerCase()">{{ shot.status || 'PENDING' }}</span>
              <button v-if="shot.status === 'PENDING'" class="btn-shot-submit" @click="submitSingleShot(i)">生成此镜</button>
              <button v-if="shot.status === 'SUCCEEDED'" class="btn-shot-play" @click="playShotVideo(i)">播放</button>
            </div>
          </div>
        </div>
      </template>
    </div>
    <!-- Translate confirmation modal -->
    <div v-if="showTranslateModal" class="modal-overlay" @click.self="showTranslateModal = false">
      <div class="translate-modal">
        <div class="translate-modal-header">
          <span>翻译结果</span>
          <button class="btn-icon-sm" @click="showTranslateModal = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="translate-columns">
          <div class="translate-col">
            <div class="translate-col-label">原文</div>
            <div class="translate-col-text">{{ translateOriginal }}</div>
          </div>
          <div class="translate-col">
            <div class="translate-col-label">译文</div>
            <div class="translate-col-text">{{ translateResult }}</div>
          </div>
        </div>
        <div class="translate-actions">
          <button class="btn-cancel" @click="showTranslateModal = false">取消</button>
          <button class="btn-replace" @click="applyTranslation">替换</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import * as api from '../api/index.js'

const props = defineProps({
  conversationId: { type: String, default: null }
})

const emit = defineEmits(['close', 'play', 'toast', 'videoSubmitted'])

const prompt = ref('')
const duration = ref(5)
const aspectRatio = ref('16:9')
const subtitleEnabled = ref(false)
const generateAudio = ref(true)
const narrateSubtitles = ref(false)
// Post-processing state
const processingTaskId = ref(null)
const processingType = ref(null) // 'subtitle' | 'narrate'
const tasksCollapsed = ref(true)
const customSubtitlesEnabled = ref(false)
const customSubtitleEntries = ref([])  // [{startSec, endSec, text}]
const showAdvanced = ref(true)
const submitting = ref(false)
const tasks = ref([])
const firstFramePreview = ref(null)
const firstFrameBase64 = ref(null)
const fileInput = ref(null)

// Storyboard mode
const mode = ref('single') // 'single' | 'storyboard'
const storyboardIdea = ref('')
const shotCount = ref(5)
const shots = ref([])
const generating = ref(false)
const storyboardTitle = ref('')
const storyboardId = ref(null)
const savingSb = ref(false)
const submittingSb = ref(false)
const savedStoryboards = ref([])
const showShotDetail = ref(null)  // index of expanded shot, or null
const resultDisplayMode = ref('individual')  // 'individual' | 'merged'
const merging = ref(false)
const mergedVideoUrl = ref(null)

const ratios = ['16:9', '9:16', '1:1']

// ── Prompt engineering state ──
const enhancing = ref(false)
const translating = ref(false)
const showTemplates = ref(false)
const showTranslateMenu = ref(false)
const showTranslateModal = ref(false)
const translateOriginal = ref('')
const translateResult = ref('')
const targetLanguage = ref('auto')
const templates = ref([])
const newTemplateName = ref('')

// Negative prompt state
const negativeTagOptions = ['模糊', '畸变', '多余手指', '文字水印', '低画质', '画面撕裂', '闪烁']
const selectedNegativeTags = ref([])
const customNegative = ref('')

const negativePrompt = computed(() => {
  const parts = [...selectedNegativeTags.value]
  if (customNegative.value.trim()) {
    parts.push(...customNegative.value.split(/[,，]/).map(s => s.trim()).filter(Boolean))
  }
  return parts.join(', ')
})

let pollTimer = null

function onDurationInput(e) {
  const val = parseFloat(e.target.value)
  if (!isNaN(val) && val > 0) {
    duration.value = Math.min(val, 12)
  }
}

function onDurationFocus(e) {
  // Select all text on focus for easy editing
  e.target.select()
}

onMounted(async () => {
  await loadTasks()
  loadTemplates()
  startPolling()
})

async function loadTasks() {
  try {
    if (props.conversationId) {
      tasks.value = await api.getConversationVideoTasks(props.conversationId)
    } else {
      tasks.value = await api.getVideoGenTasks()
    }
  } catch (e) {
    console.warn('Failed to load video tasks', e)
  }
}

async function postProcessTask(task, type) {
  if (processingTaskId.value) return
  processingTaskId.value = task.id
  processingType.value = type
  try {
    const options = {
      subtitleEnabled: type === 'subtitle',
      narrateSubtitles: type === 'narrate'
    }
    await api.postProcessVideo(task.id, options)
    emit('toast', { message: type === 'subtitle' ? '字幕已嵌入' : '配音已完成', type: 'success' })
    await loadTasks()
  } catch (e) {
    emit('toast', { message: '处理失败: ' + e.message, type: 'error' })
  } finally {
    processingTaskId.value = null
    processingType.value = null
  }
}

onUnmounted(() => {
  stopPolling()
})

function isPending(status) {
  return status === 'PENDING' || status === 'SUBMITTED' || status === 'PROCESSING'
}

function statusLabel(status) {
  const map = {
    PENDING: '等待中',
    SUBMITTED: '已提交',
    PROCESSING: '生成中',
    SUCCEEDED: '完成',
    FAILED: '失败'
  }
  return map[status] || status
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    const pendingTasks = tasks.value.filter(t => isPending(t.status))
    if (pendingTasks.length === 0) return

    for (const task of pendingTasks) {
      try {
        const updated = await api.getVideoGenTask(task.id)
        const idx = tasks.value.findIndex(t => t.id === task.id)
        if (idx >= 0) {
          tasks.value.splice(idx, 1, updated)
        }
      } catch (e) {
        console.warn('Poll failed for task', task.id, e)
      }
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function submitTask() {
  if (!prompt.value.trim() || submitting.value) return
  submitting.value = true
  try {
    const task = await api.submitVideoGen({
      prompt: prompt.value,
      duration: duration.value,
      aspectRatio: aspectRatio.value,
      seed: -1,
      firstFrameBase64: firstFrameBase64.value,
      conversationId: props.conversationId,
      generateAudio: generateAudio.value,
      negativePrompt: negativePrompt.value || null
    })
    const fullTask = {
      ...task,
      prompt: prompt.value,
      duration: duration.value,
      aspectRatio: aspectRatio.value,
      status: task.status || 'SUBMITTED',
      createdAt: task.createdAt || new Date().toISOString()
    }
    tasks.value.unshift(fullTask)
    prompt.value = ''
    firstFramePreview.value = null
    firstFrameBase64.value = null
    selectedNegativeTags.value = []
    customNegative.value = ''
    emit('videoSubmitted', fullTask)
    emit('toast', { message: '视频任务已提交', type: 'success' })
  } catch (e) {
    emit('toast', { message: '提交失败: ' + e.message, type: 'error' })
  } finally {
    submitting.value = false
  }
}

function triggerFileInput() {
  fileInput.value?.click()
}

function onFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    emit('toast', { message: '图片大小不能超过5MB', type: 'error' })
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    firstFramePreview.value = reader.result
    // Extract base64 without data:image/... prefix
    firstFrameBase64.value = reader.result.split(',')[1]
  }
  reader.readAsDataURL(file)
  fileInput.value.value = ''
}

function removeFirstFrame() {
  firstFramePreview.value = null
  firstFrameBase64.value = null
}

function addSubtitleEntry() {
  const last = customSubtitleEntries.value[customSubtitleEntries.value.length - 1]
  const startSec = last ? last.endSec : 0
  const endSec = Math.min(startSec + 2, duration.value)
  customSubtitleEntries.value.push({ startSec, endSec, text: '' })
}

function removeSubtitleEntry(index) {
  customSubtitleEntries.value.splice(index, 1)
}

// ── Prompt engineering methods ──

async function loadTemplates() {
  try {
    templates.value = await api.getVideoPromptTemplates()
  } catch (e) {
    console.warn('Failed to load video prompt templates', e)
  }
}

function toggleTemplates() { showTemplates.value = !showTemplates.value }

function selectTemplate(t) {
  prompt.value = t.content
  showTemplates.value = false
  emit('toast', { message: '已选择模板: ' + t.name, type: 'info' })
}

async function saveCurrentAsTemplate() {
  const name = newTemplateName.value.trim()
  if (!name || !prompt.value.trim()) return
  try {
    const t = await api.createVideoPromptTemplate({ name, content: prompt.value, category: 'custom' })
    templates.value.push(t)
    newTemplateName.value = ''
    emit('toast', { message: '模板已保存', type: 'success' })
  } catch (e) {
    emit('toast', { message: '保存模板失败: ' + e.message, type: 'error' })
  }
}

async function enhancePrompt() {
  if (!prompt.value.trim() || enhancing.value) return
  enhancing.value = true
  try {
    const result = await api.enhanceVideoPrompt(prompt.value)
    if (result.enhanced) prompt.value = result.enhanced
    if (result.suggestedNegative) {
      const suggestions = result.suggestedNegative.split(/[,，、]/).map(s => s.trim()).filter(Boolean)
      for (const s of suggestions) {
        const matched = negativeTagOptions.find(t => t.includes(s) || s.includes(t))
        if (matched && !selectedNegativeTags.value.includes(matched)) selectedNegativeTags.value.push(matched)
      }
      const unmatched = suggestions.filter(s => !negativeTagOptions.some(t => t.includes(s) || s.includes(t)))
      if (unmatched.length > 0) {
        const existing = customNegative.value ? customNegative.value.split(/[,，]/).map(x => x.trim()) : []
        customNegative.value = [...new Set([...existing, ...unmatched])].join(', ')
      }
    }
    emit('toast', { message: '提示词已增强', type: 'success' })
  } catch (e) {
    emit('toast', { message: '增强失败: ' + e.message, type: 'error' })
  } finally { enhancing.value = false }
}

function toggleTranslateMenu() { showTranslateMenu.value = !showTranslateMenu.value }

async function doTranslate(target) {
  showTranslateMenu.value = false
  if (!prompt.value.trim() || translating.value) return
  translating.value = true
  targetLanguage.value = target
  translateOriginal.value = prompt.value
  try {
    const result = await api.translateVideoPrompt(prompt.value, target)
    translateResult.value = result.translated
    showTranslateModal.value = true
  } catch (e) {
    emit('toast', { message: '翻译失败: ' + e.message, type: 'error' })
  } finally { translating.value = false }
}

function applyTranslation() {
  prompt.value = translateResult.value
  showTranslateModal.value = false
  emit('toast', { message: '已替换为译文', type: 'success' })
}

function toggleNegativeTag(tag) {
  const idx = selectedNegativeTags.value.indexOf(tag)
  if (idx >= 0) selectedNegativeTags.value.splice(idx, 1)
  else selectedNegativeTags.value.push(tag)
}

// ── Storyboard methods ──

async function generateStoryboard() {
  if (!storyboardIdea.value.trim() || generating.value) return
  generating.value = true
  try {
    const res = await api.generateStoryboard(storyboardIdea.value.trim(), shotCount.value)
    shots.value = (res.shots || []).map(s => ({
      ...s,
      status: 'PENDING',
      taskId: null
    }))
    storyboardId.value = null
    if (!storyboardTitle.value) {
      storyboardTitle.value = storyboardIdea.value.trim().substring(0, 50)
    }
  } catch (e) {
    emit('toast', { message: '分镜生成失败: ' + e.message, type: 'error' })
  } finally {
    generating.value = false
  }
}

async function saveStoryboard() {
  if (!storyboardTitle.value.trim() || savingSb.value) return
  savingSb.value = true
  try {
    const data = {
      conversationId: props.conversationId,
      title: storyboardTitle.value.trim(),
      idea: storyboardIdea.value.trim(),
      shotCount: shotCount.value,
      shots: shots.value.map(s => ({
        sceneNote: s.sceneNote || '',
        shotDescription: s.shotDescription,
        cameraMovement: s.cameraMovement || '固定',
        duration: s.duration || 5,
        audioHint: s.audioHint || ''
      }))
    }
    const res = await api.saveStoryboard(data)
    storyboardId.value = res.id
    if (res.shots) {
      shots.value = res.shots.map((s, i) => ({
        ...s,
        status: shots.value[i]?.status || 'PENDING',
        taskId: shots.value[i]?.taskId || null
      }))
    }
    emit('toast', { message: '分镜已保存', type: 'success' })
  } catch (e) {
    emit('toast', { message: '保存失败: ' + e.message, type: 'error' })
  } finally {
    savingSb.value = false
  }
}

function addShot() {
  shots.value.push({
    sceneNote: '',
    shotDescription: '',
    cameraMovement: '固定',
    duration: 5,
    audioHint: '',
    status: 'PENDING',
    taskId: null
  })
}

function removeShot(index) {
  shots.value.splice(index, 1)
}

function moveShot(index, direction) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= shots.value.length) return
  const temp = shots.value[index]
  shots.value[index] = shots.value[newIndex]
  shots.value[newIndex] = temp
}

async function submitAllShots() {
  if (!shots.value.length || submittingSb.value) return
  submittingSb.value = true
  try {
    const params = {
      aspectRatio: aspectRatio.value,
      negativePrompt: negativePrompt.value,
      generateAudio: generateAudio.value
    }
    if (storyboardId.value) {
      const res = await api.submitStoryboard(storyboardId.value, params)
      if (res.tasks) {
        for (const t of res.tasks) {
          const shot = shots.value.find(s => s.id === t.shotId)
          if (shot) {
            shot.status = t.status || 'SUBMITTED'
            shot.taskId = t.taskId
          }
        }
      }
      await loadTasks()
    } else {
      emit('toast', { message: '请先保存分镜再生成', type: 'error' })
    }
  } catch (e) {
    emit('toast', { message: '提交失败: ' + e.message, type: 'error' })
  } finally {
    submittingSb.value = false
  }
}

async function submitSingleShot(index) {
  const shot = shots.value[index]
  if (!shot || shot.status !== 'PENDING') return
  if (!storyboardId.value) {
    emit('toast', { message: '请先保存分镜', type: 'error' })
    return
  }
  try {
    const params = {
      aspectRatio: aspectRatio.value,
      negativePrompt: negativePrompt.value,
      generateAudio: generateAudio.value
    }
    const res = await api.submitStoryboardShot(storyboardId.value, shot.id, params)
    shot.status = res.status || 'SUBMITTED'
    shot.taskId = res.taskId
    await loadTasks()
  } catch (e) {
    emit('toast', { message: '提交失败: ' + e.message, type: 'error' })
  }
}

function playShotVideo(index) {
  const shot = shots.value[index]
  if (shot && shot.taskId) {
    const task = tasks.value.find(t => t.id === shot.taskId)
    if (task) {
      emit('play', task)
    }
  }
}

async function mergeVideos() {
  if (!storyboardId.value || merging.value) return
  merging.value = true
  mergedVideoUrl.value = null
  try {
    const res = await api.mergeStoryboardVideos(storyboardId.value)
    mergedVideoUrl.value = api.getMergedVideoUrl(storyboardId.value)
    emit('toast', { message: '视频合并完成', type: 'success' })
  } catch (e) {
    emit('toast', { message: '合并失败: ' + e.message, type: 'error' })
  } finally {
    merging.value = false
  }
}
</script>

<style scoped>
.video-gen-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.vgp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.vgp-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.vgp-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.vgp-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}

.task-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
  padding: 6px 0;
  border-radius: 4px;
  transition: color 0.15s;
}
.task-list-header:hover {
  color: var(--text-secondary);
}

.collapse-chevron {
  flex-shrink: 0;
  transition: transform 0.2s;
}
.collapse-chevron.collapsed {
  transform: rotate(-90deg);
}

/* Task list */
.vgp-task-list {
  margin-bottom: 24px;
}

.vgp-task-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 6px;
}

.task-status-icon {
  flex-shrink: 0;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.spin {
  animation: spin 1.5s linear infinite;
}

@keyduration spin {
  to { transform: rotate(360deg); }
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-prompt {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-meta {
  font-size: 11px;
  margin-top: 2px;
}

.status-pending, .status-submitted, .status-processing {
  color: var(--accent);
}

.status-succeeded {
  color: var(--success);
}

.status-failed {
  color: var(--danger);
}

.task-error {
  color: var(--danger);
  margin-left: 6px;
}

.task-actions {
  flex-shrink: 0;
  display: flex;
  gap: 4px;
}

.btn-play {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  background: var(--success);
  color: white;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.btn-play:hover {
  opacity: 0.85;
}

.btn-post-process {
  padding: 2px 8px;
  border: 1px solid var(--primary);
  border-radius: 4px;
  background: transparent;
  color: var(--primary);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
}
.btn-post-process:hover:not(:disabled) { background: var(--primary); color: #fff; }
.btn-post-process:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-delete-task {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 4px;
  padding: 0;
}
.btn-delete-task:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

/* Form */
.vgp-form {
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
}

.form-group {
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
  line-height: 1.5;
}
.form-textarea::placeholder {
  color: var(--text-muted);
}
.form-textarea:focus {
  border-color: var(--accent);
  outline: none;
}

.btn-upload-img {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.btn-upload-img:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.first-frame-preview {
  position: relative;
  display: inline-block;
}
.first-frame-preview img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}
.btn-remove-img {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--danger);
  color: white;
  border-radius: 50%;
  padding: 0;
}

.file-hidden {
  display: none;
}

.btn-toggle-advanced {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  color: var(--text-muted);
  font-size: 12px;
  padding: 0;
}
.btn-toggle-advanced:hover {
  color: var(--text-secondary);
}
.btn-toggle-advanced svg {
  transition: transform 0.15s;
}
.btn-toggle-advanced svg.rotated {
  transform: rotate(180deg);
}

.advanced-settings {
  padding: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 14px;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.form-row:last-child {
  margin-bottom: 0;
}

.btn-group {
  display: flex;
  gap: 4px;
}

.btn-option {
  padding: 5px 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}
.btn-option.active {
  background: var(--accent);
  border-color: var(--accent);
  color: white;
}

.duration-input-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.duration-custom {
  display: flex;
  align-items: center;
  gap: 2px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  padding: 0 6px;
  transition: border-color 0.15s;
}
.duration-custom:focus-within {
  border-color: var(--accent);
}

.duration-input {
  width: 48px;
  padding: 4px 2px;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 12px;
  font-family: inherit;
  text-align: center;
  outline: none;
  -moz-appearance: textfield;
}
.duration-input::-webkit-outer-spin-button,
.duration-input::-webkit-inner-spin-button {
  opacity: 1;
}

.duration-unit {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.btn-submit {
  width: 100%;
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  margin-top: 8px;
  transition: background 0.15s;
}
.btn-submit:hover:not(:disabled) {
  background: var(--accent-hover);
}
.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ── Custom subtitle editor ── */
.custom-subtitles-editor {
  padding: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 10px;
}

.subtitle-entries {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.subtitle-entry {
  background: var(--bg-hover);
  border-radius: 6px;
  padding: 8px 10px;
}

.subtitle-entry-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.entry-index {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
}

.btn-remove-entry {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 4px;
  font-size: 10px;
  padding: 0;
}
.btn-remove-entry:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

.entry-time-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.entry-time {
  width: 58px;
  padding: 4px 6px;
  background: var(--bg-input, var(--bg-card));
  border: 1px solid var(--border-color);
  border-radius: 5px;
  color: var(--text-primary);
  font-size: 12px;
  font-family: inherit;
  text-align: center;
  outline: none;
}
.entry-time:focus {
  border-color: var(--accent);
}

.time-sep {
  color: var(--text-muted);
  font-size: 12px;
}

.time-unit {
  font-size: 11px;
  color: var(--text-muted);
}

.entry-text {
  width: 100%;
  padding: 5px 8px;
  background: var(--bg-input, var(--bg-card));
  border: 1px solid var(--border-color);
  border-radius: 5px;
  color: var(--text-primary);
  font-size: 12px;
  font-family: inherit;
  outline: none;
  box-sizing: border-box;
}
.entry-text:focus {
  border-color: var(--accent);
}
.entry-text::placeholder {
  color: var(--text-muted);
}

.btn-add-subtitle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  background: var(--bg-hover);
  border: 1px dashed var(--border-color);
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}
.btn-add-subtitle:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.btn-icon-sm {
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 6px;
}
.btn-icon-sm:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* Toggle switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
}
.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: all 0.2s;
}
.toggle-slider::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 2px;
  top: 2px;
  background: var(--text-muted);
  border-radius: 50%;
  transition: all 0.2s;
}
.toggle-switch input:checked + .toggle-slider {
  background: var(--accent);
  border-color: var(--accent);
}
.toggle-switch input:checked + .toggle-slider::before {
  background: white;
  transform: translateX(20px);
}

/* ── Prompt toolbar ── */
.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  margin-bottom: 6px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
}
.btn-tool {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  transition: all 0.15s;
}
.btn-tool:hover:not(:disabled) {
  background: var(--bg-card);
  border-color: var(--accent);
  color: var(--accent);
}
.btn-tool.active {
  background: var(--accent);
  border-color: var(--accent);
  color: white;
}
.btn-tool:disabled { opacity: 0.4; cursor: not-allowed; }
.char-count {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}

/* ── Translate dropdown ── */
.translate-wrapper { position: relative; }
.translate-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  z-index: 20;
  min-width: 100px;
  overflow: hidden;
}
.translate-menu button {
  display: block;
  width: 100%;
  padding: 8px 14px;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.translate-menu button:hover { background: var(--bg-hover); }

/* ── Template selector ── */
.template-selector {
  margin-bottom: 12px;
  padding: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.template-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.chip-template {
  padding: 5px 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--text-secondary);
  font-size: 12px;
  transition: all 0.15s;
}
.chip-template:hover { border-color: var(--accent); color: var(--accent); }
.chip-template.preset { border-style: solid; }
.template-add-row {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 8px;
  width: 100%;
}
.input-new-template {
  flex: 1;
  padding: 5px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  min-width: 0;
}
.input-new-template:focus { border-color: var(--accent); }
.btn-save-template {
  padding: 5px 12px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}
.btn-save-template:disabled { opacity: 0.4; cursor: not-allowed; }

/* ── Translate modal ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.translate-modal {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  width: 90%;
  max-width: 560px;
  max-height: 80vh;
  overflow-y: auto;
}
.translate-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
  font-size: 14px;
  font-weight: 600;
}
.translate-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 16px;
}
.translate-col-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 6px;
}
.translate-col-text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
}
.translate-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
}
.btn-cancel {
  padding: 7px 16px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}
.btn-replace {
  padding: 7px 16px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
}

/* ── Negative prompt ── */
.negative-prompt-section {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
}
.negative-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.tag-negative {
  padding: 4px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  color: var(--text-muted);
  font-size: 11px;
  transition: all 0.15s;
}
.tag-negative:hover { border-color: var(--danger); color: var(--danger); }
.tag-negative.selected {
  background: color-mix(in srgb, var(--danger) 15%, transparent);
  border-color: var(--danger);
  color: var(--danger);
}
.input-custom-negative {
  width: 100%;
  padding: 7px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
}
.input-custom-negative:focus { border-color: var(--accent); }
.input-custom-negative::placeholder { color: var(--text-muted); }

/* ── Mode tabs ── */
.vgp-mode-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
  flex-shrink: 0;
}
.mode-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.mode-tab:hover { border-color: var(--accent); color: var(--accent); }
.mode-tab.active { background: var(--accent); color: #fff; border-color: var(--accent); }

/* ── Storyboard ── */
.storyboard-section {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
}
.sb-idea-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.sb-shot-count { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); }
.shot-count-input { width: 50px; padding: 4px 6px; border: 1px solid var(--border-color); border-radius: 4px; text-align: center; background: var(--bg); color: var(--text); }
.btn-generate-sb {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; background: linear-gradient(135deg, #667eea, #764ba2); color: #fff;
  border: none; border-radius: 8px; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: opacity 0.2s;
}
.btn-generate-sb:hover:not(:disabled) { opacity: 0.9; }
.btn-generate-sb:disabled { opacity: 0.5; cursor: not-allowed; }

.sb-title-row { display: flex; gap: 8px; margin-bottom: 10px; }
.sb-title-input { flex: 1; padding: 6px 10px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg); color: var(--text); font-size: 14px; }
.btn-save-sb {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 14px; background: var(--success); color: #fff;
  border: none; border-radius: 6px; font-size: 12px; cursor: pointer;
}
.btn-save-sb:disabled { opacity: 0.5; cursor: not-allowed; }

.shot-table { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; }
.shot-table-header {
  display: grid; grid-template-columns: 40px 1fr 80px 60px 80px 24px; gap: 6px;
  padding: 8px 10px; background: var(--bg-secondary); font-size: 12px; font-weight: 600; color: var(--text-secondary);
}
.shot-row {
  display: grid; grid-template-columns: 40px 1fr 80px 60px 80px 24px; gap: 6px;
  padding: 8px 10px; border-top: 1px solid var(--border-color); align-items: center;
}
.shot-row:hover { background: var(--bg-hover); }
.col-num { text-align: center; font-weight: 600; font-size: 13px; }
.shot-desc-input {
  width: 100%; padding: 4px 6px; border: 1px solid var(--border-color); border-radius: 4px;
  background: var(--bg); color: var(--text); font-size: 12px; resize: vertical; min-height: 36px;
  box-sizing: border-box;
}
.shot-cam-select {
  padding: 4px; border: 1px solid var(--border-color); border-radius: 4px;
  background: var(--bg); color: var(--text); font-size: 12px;
}
.shot-dur-input { width: 100%; padding: 4px; border: 1px solid var(--border-color); border-radius: 4px; background: var(--bg); color: var(--text); text-align: center; font-size: 12px; }
.col-actions { display: flex; gap: 2px; justify-content: center; }
.btn-shot-action { padding: 2px 6px; border: 1px solid var(--border-color); border-radius: 3px; background: var(--bg); color: var(--text-secondary); font-size: 11px; cursor: pointer; }
.btn-shot-action:hover { background: var(--bg-hover); }
.btn-shot-action:disabled { opacity: 0.3; cursor: not-allowed; }
.btn-shot-delete { color: var(--danger); border-color: transparent; }

.btn-add-shot {
  display: flex; align-items: center; gap: 4px; margin-top: 8px;
  padding: 6px 12px; border: 1px dashed var(--border-color); border-radius: 6px;
  background: transparent; color: var(--text-secondary); font-size: 12px; cursor: pointer; width: 100%; justify-content: center;
}
.btn-add-shot:hover { border-color: var(--accent); color: var(--accent); }

.shot-status-list { margin-top: 12px; }
.shot-status-row {
  display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-top: 1px solid var(--border-color); font-size: 13px;
}
.shot-status-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500; }
.shot-status-badge.status-pending { background: var(--bg-secondary); color: var(--text-secondary); }
.shot-status-badge.status-submitted { background: #dbeafe; color: #2563eb; }
.shot-status-badge.status-processing { background: #fef3c7; color: #d97706; }
.shot-status-badge.status-succeeded { background: #d1fae5; color: #059669; }
.shot-status-badge.status-failed { background: #fee2e2; color: #dc2626; }
.btn-shot-submit, .btn-shot-play {
  margin-left: auto; padding: 3px 10px; border: 1px solid var(--accent); border-radius: 4px;
  background: transparent; color: var(--accent); font-size: 11px; cursor: pointer;
}
.btn-shot-submit:hover, .btn-shot-play:hover { background: var(--accent); color: #fff; }

/* ── Expandable shot details ── */
.btn-shot-expand { padding: 2px 4px; border: none; background: transparent; color: var(--text-secondary); cursor: pointer; }
.btn-shot-expand:hover { color: var(--text); }
.btn-shot-expand svg.rotated { transform: rotate(180deg); }
.shot-detail-row { padding: 8px 10px 8px 46px; background: var(--bg-secondary); border-top: 1px solid var(--border); }
.shot-detail-fields { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.shot-detail-fields label { color: var(--text-secondary); white-space: nowrap; }
.shot-detail-input { flex: 1; padding: 4px 6px; border: 1px solid var(--border); border-radius: 4px; background: var(--bg); color: var(--text); font-size: 12px; }

/* ── Merged display mode ── */
.sb-display-mode { margin-bottom: 12px; }
.sb-display-mode .display-label { display: block; font-size: 13px; font-weight: 600; color: var(--text); margin-bottom: 8px; }
.display-mode-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.display-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 8px; border: 2px solid var(--border); border-radius: 10px;
  background: var(--bg); cursor: pointer; transition: all 0.2s; text-align: center;
  position: relative; opacity: 0.55;
}
.display-card:hover { border-color: var(--primary); opacity: 0.8; }
.display-card.active {
  border-color: var(--primary);
  background: linear-gradient(135deg, color-mix(in srgb, var(--primary) 15%, var(--bg)), color-mix(in srgb, var(--primary) 5%, var(--bg)));
  opacity: 1;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 25%, transparent);
}
.display-card-icon { color: var(--text-secondary); transition: color 0.2s; }
.display-card.active .display-card-icon { color: var(--primary); }
.display-card-title { font-size: 13px; font-weight: 600; color: var(--text); }
.display-card-desc { font-size: 11px; color: var(--text-secondary); }
.display-card.active .display-card-title { color: var(--primary); }
.display-card::after {
  content: "✓"; position: absolute; top: 6px; right: 8px;
  font-size: 12px; font-weight: 700; color: transparent; transition: color 0.2s;
}
.display-card.active::after { color: var(--primary); }
/* Shot summary (merged mode read-only) */
.shot-summary-section { opacity: 0.85; }
.shot-summary-list { display: flex; flex-direction: column; gap: 8px; }
.shot-summary-item { display: flex; gap: 10px; align-items: flex-start; padding: 8px; border-radius: 6px; background: var(--bg-secondary); }
.summary-num {
  flex-shrink: 0; width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; background: var(--bg); color: var(--text-secondary);
}
.summary-num.summary-status-succeeded { background: #d1fae5; color: #059669; }
.summary-num.summary-status-failed { background: #fee2e2; color: #dc2626; }
.summary-num.summary-status-submitted, .summary-num.summary-status-processing { background: #fef3c7; color: #d97706; }
.summary-content { flex: 1; min-width: 0; }
.summary-desc { margin: 0; font-size: 12px; line-height: 1.5; color: var(--text); }
.summary-meta { font-size: 11px; color: var(--text-secondary); }

.merged-section { margin-top: 12px; }
.btn-merged-play { display: flex; align-items: center; gap: 6px; padding: 10px 16px; background: linear-gradient(135deg, #f59e0b, #d97706); color: #fff; border: none; border-radius: 8px; font-size: 13px; cursor: pointer; width: 100%; justify-content: center; }
.btn-merged-play:hover:not(:disabled) { opacity: 0.9; }
.btn-merged-play:disabled { opacity: 0.5; cursor: not-allowed; }
.merged-hint { font-size: 11px; color: var(--text-secondary); text-align: center; margin-top: 4px; }
.merging-status { display: flex; align-items: center; gap: 10px; justify-content: center; padding: 20px; color: var(--text-secondary); font-size: 14px; }
.merged-video-player { width: 100%; max-height: 400px; border-radius: 8px; margin-top: 8px; background: #000; }
</style>
